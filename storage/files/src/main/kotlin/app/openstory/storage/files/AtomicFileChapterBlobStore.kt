package app.openstory.storage.files

import android.content.Context
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobStore
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android app-private [ChapterBlobStore] implementation with staged atomic writes. */
class AtomicFileChapterBlobStore internal constructor(
    private val rootDirectory: File,
    private val files: BlobFileOperations,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChapterBlobStore {
    constructor(context: Context) : this(
        rootDirectory = ChapterBlobFileLayout.root(context),
        files = PlatformBlobFileOperations,
        ioDispatcher = Dispatchers.IO,
    )

    override suspend fun read(key: ChapterBlobKey): ChapterBlob? {
        val target = blobFile(key)
        return ChapterBlobFileLocks.withLock(target) {
            withContext(ioDispatcher) {
                val encoded = files.readBytes(target) ?: return@withContext null
                val blob = decode(encoded)
                if (blob == null) {
                    files.delete(target)
                }
                blob
            }
        }
    }

    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) {
        val target = blobFile(key)
        ChapterBlobFileLocks.withLock(target) {
            withContext(ioDispatcher) {
                val temporary = files.createTempFile(target.parentFile ?: error("Blob file has no parent."))
                try {
                    files.openOutput(temporary).use { output ->
                        output.write(blob.checksum.value.encodeToByteArray())
                        output.write(NEWLINE_BYTES)
                        output.writeBlob(blob)
                        output.sync()
                    }
                    files.moveAtomically(temporary, target)
                } finally {
                    if (files.exists(temporary)) {
                        files.delete(temporary)
                    }
                }
            }
        }
    }

    override suspend fun delete(key: ChapterBlobKey) {
        val target = blobFile(key)
        ChapterBlobFileLocks.withLock(target) {
            withContext(ioDispatcher) {
                files.delete(target)
            }
        }
    }

    override suspend fun deleteIfPresent(key: ChapterBlobKey): Boolean {
        val target = blobFile(key)
        return ChapterBlobFileLocks.withLock(target) {
            withContext(ioDispatcher) {
                if (!files.exists(target)) {
                    false
                } else {
                    files.delete(target)
                    true
                }
            }
        }
    }

    private fun blobFile(key: ChapterBlobKey): File = ChapterBlobFileLayout.blobFile(rootDirectory, key)

    private fun decode(encoded: ByteArray): ChapterBlob? = try {
        if (encoded.size <= CHECKSUM_LENGTH || encoded[CHECKSUM_LENGTH] != NEWLINE) {
            null
        } else {
            ChapterBlob.verified(
                bytes = encoded,
                offset = CHECKSUM_LENGTH + 1,
                length = encoded.size - CHECKSUM_LENGTH - 1,
                checksum = BlobChecksum(encoded.decodeToString(0, CHECKSUM_LENGTH)),
            )
        }
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val CHECKSUM_LENGTH = 64
        const val NEWLINE: Byte = '\n'.code.toByte()
        val NEWLINE_BYTES = byteArrayOf(NEWLINE)
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

    fun writeBlob(blob: ChapterBlob) {
        write(blob.bytes())
    }

    fun sync()
}

internal object PlatformBlobFileOperations : BlobFileOperations {
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

            override fun writeBlob(blob: ChapterBlob) {
                blob.inputStream().use { input -> input.copyTo(stream) }
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
