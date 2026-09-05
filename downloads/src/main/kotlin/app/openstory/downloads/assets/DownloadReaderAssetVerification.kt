package app.openstory.downloads.assets

import app.openstory.downloads.blob.BlobChecksum
import app.openstory.reader.assets.ReaderAssetReadLease
import app.openstory.reader.assets.ReaderAssetRuntimePolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

internal sealed interface ReaderAssetVerification {
    data class Verified(val bytes: ByteArray) : ReaderAssetVerification
    data object Corrupt : ReaderAssetVerification
    data object Unavailable : ReaderAssetVerification
}

internal fun verifyReaderAssetLease(
    physicalLease: ReaderAssetBlobReadLease,
    metadata: ReaderAssetMetadata,
): ReaderAssetVerification = try {
    if (physicalLease.sizeBytes != metadata.byteSize ||
        metadata.byteSize !in 1L..ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES.toLong()
    ) {
        ReaderAssetVerification.Corrupt
    } else {
        val bytes = physicalLease.openStream().use(::readBoundedReaderAsset)
        if (bytes.size.toLong() == metadata.byteSize && BlobChecksum.sha256(bytes) == metadata.localBlobChecksum) {
            ReaderAssetVerification.Verified(bytes)
        } else {
            ReaderAssetVerification.Corrupt
        }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: ReaderAssetCorruptionException) {
    ReaderAssetVerification.Corrupt
} catch (_: Exception) {
    ReaderAssetVerification.Unavailable
}

internal fun StoredReaderAssetBlob.matches(blobId: ReaderAssetBlobId, bytes: ByteArray): Boolean =
    id == blobId && sizeBytes == bytes.size.toLong() && checksum == BlobChecksum.sha256(bytes)

internal class VerifiedReaderAssetReadLease(
    private val bytes: ByteArray,
    private val physicalLease: ReaderAssetBlobReadLease,
) : ReaderAssetReadLease {
    private val closed = AtomicBoolean(false)

    override val sizeBytes: Long = bytes.size.toLong()

    override fun openStream(): InputStream {
        check(!closed.get()) { "Reader asset lease is closed." }
        return ByteArrayInputStream(bytes)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) physicalLease.close()
    }
}

internal fun ReaderAssetBlobReadLease.closeBestEffort() {
    runCatching(::close)
}

private fun readBoundedReaderAsset(stream: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(READ_BUFFER_BYTES)
    var total = 0
    while (total <= ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES) {
        val read = stream.read(buffer)
        if (read < 0) return output.toByteArray()
        if (read > 0) {
            total += read
            if (total > ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES) throw ReaderAssetCorruptionException()
            output.write(buffer, 0, read)
        }
    }
    throw ReaderAssetCorruptionException()
}

private class ReaderAssetCorruptionException : Exception()
private const val READ_BUFFER_BYTES = 8 * 1024
