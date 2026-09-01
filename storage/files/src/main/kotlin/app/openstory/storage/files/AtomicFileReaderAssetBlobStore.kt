package app.openstory.storage.files

import android.content.Context
import android.system.ErrnoException
import app.openstory.downloads.assets.ReaderAssetBlobId
import app.openstory.downloads.assets.ReaderAssetBlobReadLease
import app.openstory.downloads.assets.ReaderAssetBlobStore
import app.openstory.downloads.assets.ReaderAssetBlobWriteResult
import app.openstory.downloads.assets.StoredReaderAssetBlob
import app.openstory.downloads.assets.requireReaderAssetBlobPayloadSize
import app.openstory.downloads.blob.BlobChecksum
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AtomicFileReaderAssetBlobStore internal constructor(
    private val rootDirectory: File,
    private val files: ReaderAssetBlobFileOperations,
    private val locks: ReaderAssetBlobFileLocks = ReaderAssetBlobFileLocks.Shared,
    private val errorClassifier: ReaderAssetStorageErrorClassifier = ReaderAssetStorageErrorClassifier.Platform,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReaderAssetBlobStore {
    constructor(context: Context) : this(
        rootDirectory = ReaderAssetBlobFileLayout.root(context),
        files = PlatformReaderAssetBlobFileOperations,
    )

    override suspend fun writeAtomic(
        id: ReaderAssetBlobId,
        bytes: ByteArray,
    ): ReaderAssetBlobWriteResult {
        requireReaderAssetBlobPayloadSize(bytes)
        val target = ReaderAssetBlobFileLayout.blobFile(rootDirectory, id)
        val checksum = BlobChecksum.sha256(bytes)
        return locks.withLock(target) {
            withContext(ioDispatcher) {
                var temporary: File? = null
                try {
                    temporary = files.createTempFile(target.parentFile ?: error("Blob file has no parent."))
                    files.openOutput(temporary).use { output ->
                        output.write(bytes)
                        output.sync()
                    }
                    files.moveAtomically(temporary, target)
                    ReaderAssetBlobWriteResult.Stored(
                        StoredReaderAssetBlob(id, bytes.size.toLong(), checksum),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IOException) {
                    classifyFailure(failure)
                } catch (failure: ErrnoException) {
                    classifyFailure(failure)
                } catch (failure: SecurityException) {
                    ReaderAssetBlobWriteResult.Unavailable(failure)
                } finally {
                    temporary?.takeIf(files::exists)?.let { staged ->
                        runCatching { files.delete(staged) }
                    }
                }
            }
        }
    }

    override suspend fun open(id: ReaderAssetBlobId): ReaderAssetBlobReadLease? {
        val target = ReaderAssetBlobFileLayout.blobFile(rootDirectory, id)
        return locks.withLock(target) { entry ->
            withContext(ioDispatcher) {
                val stream = files.openInput(target) ?: return@withContext null
                val token = locks.acquireLease(target, entry)
                if (token == null) {
                    stream.close()
                    null
                } else {
                    FileReaderAssetBlobReadLease(files.size(target), stream, locks, token)
                }
            }
        }
    }

    override suspend fun exists(id: ReaderAssetBlobId): Boolean {
        val target = ReaderAssetBlobFileLayout.blobFile(rootDirectory, id)
        return locks.withLock(target) { withContext(ioDispatcher) { files.exists(target) } }
    }

    override suspend fun tryDeleteNowIfUnleased(id: ReaderAssetBlobId): Boolean {
        val target = ReaderAssetBlobFileLayout.blobFile(rootDirectory, id)
        return locks.withLock(target) { entry ->
            if (locks.hasActiveLeases(entry)) {
                false
            } else {
                withContext(ioDispatcher) { files.delete(target) }
            }
        }
    }

    override suspend fun deleteWhenUnleased(id: ReaderAssetBlobId) {
        val target = ReaderAssetBlobFileLayout.blobFile(rootDirectory, id)
        lateinit var request: ReaderAssetBlobFileLocks.DeleteRequest
        locks.withLock(target) { entry -> request = locks.requestDelete(target, entry) }
        try {
            request.zeroLeases.await()
            locks.withLock(target) { withContext(ioDispatcher) { files.delete(target) } }
        } finally {
            locks.finishDelete(request)
        }
    }

    private fun classifyFailure(failure: Throwable): ReaderAssetBlobWriteResult =
        if (errorClassifier.isNoSpace(failure)) {
            ReaderAssetBlobWriteResult.NoSpace
        } else {
            ReaderAssetBlobWriteResult.Unavailable(failure)
        }
}

private class FileReaderAssetBlobReadLease(
    override val sizeBytes: Long,
    private val stream: InputStream,
    private val locks: ReaderAssetBlobFileLocks,
    private val token: ReaderAssetBlobFileLocks.LeaseToken,
) : ReaderAssetBlobReadLease {
    private val closed = AtomicBoolean()

    override fun openStream(): InputStream {
        check(!closed.get()) { "Reader asset blob lease is closed." }
        return stream
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            stream.close()
        } finally {
            locks.releaseLease(token)
        }
    }
}

internal interface ReaderAssetBlobFileOperations {
    fun createTempFile(parent: File): File
    fun openOutput(file: File): BlobFileOutput
    fun moveAtomically(source: File, target: File)
    fun openInput(file: File): InputStream?
    fun delete(file: File): Boolean
    fun exists(file: File): Boolean
    fun size(file: File): Long
}

internal object PlatformReaderAssetBlobFileOperations : ReaderAssetBlobFileOperations {
    override fun createTempFile(parent: File): File {
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create app-private reader asset directory.")
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

    override fun openInput(file: File): InputStream? = if (file.exists()) FileInputStream(file) else null

    override fun delete(file: File): Boolean {
        if (!file.exists()) return false
        if (!file.delete()) throw IOException("Could not delete app-private reader asset blob.")
        return true
    }

    override fun exists(file: File): Boolean = file.exists()

    override fun size(file: File): Long = file.length()
}

internal object ReaderAssetBlobFileLayout {
    const val ROOT_DIRECTORY_NAME = "reader-assets"
    val BLOB_FILE = Regex("[0-9a-f]{64}\\.blob")
    val TEMP_FILE = Regex("\\.stage-[A-Za-z0-9._-]+\\.tmp")

    fun root(context: Context): File = File(context.filesDir, ROOT_DIRECTORY_NAME)

    fun blobFile(root: File, id: ReaderAssetBlobId): File {
        val target = File(root, "${id.value}.blob")
        check(target.toPath().normalize().startsWith(root.toPath().normalize())) {
            "Reader asset blob path escaped its app-private root."
        }
        return target
    }
}
