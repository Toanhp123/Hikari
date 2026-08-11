package app.openstory.downloads

import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlobKey

enum class DownloadState { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

data class DownloadRecord(
    val key: ChapterBlobKey,
    val state: DownloadState,
    val checksum: BlobChecksum? = null,
    val sizeBytes: Long = 0,
    val failureReason: String? = null,
    val attempt: Int = 0,
    val updatedAtEpochMillis: Long,
)

sealed interface DownloadFetchResult {
    data class Success(val bytes: ByteArray, val checksum: BlobChecksum) : DownloadFetchResult
    data class Failure(val code: String, val retryable: Boolean) : DownloadFetchResult
}

fun interface DownloadContentSource {
    suspend fun fetch(key: ChapterBlobKey): DownloadFetchResult
}

enum class DownloadRunResult { COMPLETED, RETRY, FAILURE, CANCELLED }

fun interface DownloadScheduler {
    fun schedule(key: ChapterBlobKey)
}
