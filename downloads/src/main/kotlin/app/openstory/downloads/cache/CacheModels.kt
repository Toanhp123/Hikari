package app.openstory.downloads.cache

import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlobKey

data class CacheEntry(
    val key: ChapterBlobKey,
    val checksum: BlobChecksum,
    val sizeBytes: Long,
    val lastAccessedAtEpochMillis: Long,
    val pinned: Boolean = false,
    val current: Boolean = false,
) {
    init {
        require(sizeBytes >= 0) { "Cache size must not be negative." }
    }
}

data class CacheEvictionPlan(
    val keys: List<ChapterBlobKey>,
    val retainedBytes: Long,
    val quotaBytes: Long,
) {
    val protectedBytesExceedQuota: Boolean = retainedBytes > quotaBytes
}
