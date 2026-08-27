package app.openstory.downloads.reader

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadState
import app.openstory.downloads.blob.ChapterBlobNamespace

data class ReaderCacheMetadata(
    val releaseId: ChapterReleaseId,
    val fingerprint: String,
    val namespace: ChapterBlobNamespace,
    val checksumPresent: Boolean,
    val downloadState: DownloadState?,
    val lastAccessedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(fingerprint.isNotBlank()) { "Reader cache metadata fingerprint must not be blank." }
        require(lastAccessedAtEpochMillis >= 0L) { "Reader cache access time must be non-negative." }
        require(updatedAtEpochMillis >= 0L) { "Reader cache update time must be non-negative." }
    }
}

fun interface ReaderCacheMetadataSource {
    suspend fun entriesFor(releaseIds: Set<ChapterReleaseId>): List<ReaderCacheMetadata>
}
