package app.openstory.downloads.assets

import app.openstory.downloads.blob.BlobChecksum
import app.openstory.reader.assets.ReaderAssetRuntimePolicy
import java.io.InputStream

@JvmInline
value class ReaderAssetBlobId(val value: String) {
    init {
        require(value.matches(SHA256)) { "Reader asset blob ID must be a lowercase SHA-256 hex string." }
    }

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

data class StoredReaderAssetBlob(
    val id: ReaderAssetBlobId,
    val sizeBytes: Long,
    val checksum: BlobChecksum,
)

sealed interface ReaderAssetBlobWriteResult {
    data class Stored(val blob: StoredReaderAssetBlob) : ReaderAssetBlobWriteResult
    data object NoSpace : ReaderAssetBlobWriteResult
    data class Unavailable(val cause: Throwable) : ReaderAssetBlobWriteResult
}

interface ReaderAssetBlobReadLease : AutoCloseable {
    val sizeBytes: Long
    fun openStream(): InputStream
}

interface ReaderAssetBlobStore {
    suspend fun writeAtomic(id: ReaderAssetBlobId, bytes: ByteArray): ReaderAssetBlobWriteResult
    suspend fun open(id: ReaderAssetBlobId): ReaderAssetBlobReadLease?
    suspend fun exists(id: ReaderAssetBlobId): Boolean
    suspend fun tryDeleteNowIfUnleased(id: ReaderAssetBlobId): Boolean
    suspend fun deleteWhenUnleased(id: ReaderAssetBlobId)
}

fun requireReaderAssetBlobPayloadSize(bytes: ByteArray) {
    require(bytes.size <= ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES) {
        "Reader asset blob exceeds the encoded payload bound."
    }
}
