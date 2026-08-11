package app.openstory.storage.files

import android.content.Context
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/** Android app-private [ChapterBlobStore] implementation with staged atomic writes. */
class AtomicFileChapterBlobStore internal constructor(
    private val rootDirectory: File,
    private val files: BlobFileOperations,
) : ChapterBlobStore {
    private val operationLock = Any()

    constructor(context: Context) : this(
        rootDirectory = File(context.filesDir, ROOT_DIRECTORY_NAME),
        files = PlatformBlobFileOperations,
    )

    override suspend fun read(key: ChapterBlobKey): ChapterBlob? {
        return synchronized(operationLock) {
            val target = blobFile(key)
            val encoded = files.readBytes(target) ?: return@synchronized null
            val blob = decode(encoded)
            if (blob == null) {
                files.delete(target)
            }
            blob
        }
    }

    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = synchronized(operationLock) {
        val target = blobFile(key)
        val temporary = files.createTempFile(target.parentFile ?: error("Blob file has no parent."))
        try {
            files.openOutput(temporary).use { output ->
                output.write(encode(blob))
                output.sync()
            }
            files.moveAtomically(temporary, target)
        } finally {
            if (files.exists(temporary)) {
                files.delete(temporary)
            }
        }
    }

    override suspend fun delete(key: ChapterBlobKey) = synchronized(operationLock) {
        files.delete(blobFile(key))
    }

    private fun blobFile(key: ChapterBlobKey): File {
        val namespaceDirectory = File(rootDirectory, key.namespace.directoryName)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${key.releaseId.value}\u0000${key.contentFingerprint}".encodeToByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        val target = File(namespaceDirectory, "$digest.blob")
        check(target.toPath().normalize().startsWith(rootDirectory.toPath().normalize())) {
            "Blob path escaped its app-private root."
        }
        return target
    }

    private fun encode(blob: ChapterBlob): ByteArray =
        blob.checksum.value.encodeToByteArray() + byteArrayOf(NEWLINE) + blob.bytes()

    private fun decode(encoded: ByteArray): ChapterBlob? = try {
        if (encoded.size <= CHECKSUM_LENGTH || encoded[CHECKSUM_LENGTH] != NEWLINE) {
            null
        } else {
            ChapterBlob.verified(
                bytes = encoded.copyOfRange(CHECKSUM_LENGTH + 1, encoded.size),
                checksum = BlobChecksum(encoded.copyOfRange(0, CHECKSUM_LENGTH).decodeToString()),
            )
        }
    } catch (_: IllegalArgumentException) {
        null
    }

    private val ChapterBlobNamespace.directoryName: String
        get() = when (this) {
            ChapterBlobNamespace.AUTOMATIC_CACHE -> "cache"
            ChapterBlobNamespace.EXPLICIT_DOWNLOAD -> "downloads"
        }

    private companion object {
        const val ROOT_DIRECTORY_NAME = "chapter-blobs"
        const val CHECKSUM_LENGTH = 64
        const val NEWLINE: Byte = '\n'.code.toByte()
    }
}

internal interface BlobFileOperations {
    fun createTempFile(parent: File): File

    fun openOutput(file: File): BlobFileOutput

    fun moveAtomically(source: File, target: File)

    fun readBytes(file: File): ByteArray?

    fun delete(file: File)

    fun exists(file: File): Boolean
}

internal interface BlobFileOutput : Closeable {
    fun write(bytes: ByteArray)

    fun sync()
}

private object PlatformBlobFileOperations : BlobFileOperations {
    override fun createTempFile(parent: File): File {
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create app-private blob directory.")
        }
        return File.createTempFile(".stage-", ".tmp", parent)
    }

    override fun openOutput(file: File): BlobFileOutput {
        val stream = FileOutputStream(file)
        return object : BlobFileOutput {
            override fun write(bytes: ByteArray) {
                stream.write(bytes)
            }

            override fun sync() {
                stream.fd.sync()
            }

            override fun close() {
                stream.close()
            }
        }
    }

    override fun moveAtomically(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    }

    override fun readBytes(file: File): ByteArray? = if (file.exists()) file.readBytes() else null

    override fun delete(file: File) {
        if (file.exists() && !file.delete()) {
            throw IOException("Could not delete app-private blob.")
        }
    }

    override fun exists(file: File): Boolean = file.exists()
}
