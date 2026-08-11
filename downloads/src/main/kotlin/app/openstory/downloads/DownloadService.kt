package app.openstory.downloads

import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class DownloadService(
    private val repository: DownloadRepository,
    private val blobs: ChapterBlobStore,
    private val source: DownloadContentSource,
) {
    suspend fun queue(key: ChapterBlobKey, now: Long) {
        require(key.namespace == ChapterBlobNamespace.EXPLICIT_DOWNLOAD)
        val existing = repository.find(key)
        if (existing?.state != DownloadState.COMPLETED) {
            repository.save(DownloadRecord(key, DownloadState.QUEUED, attempt = existing?.attempt ?: 0, updatedAtEpochMillis = now))
        }
    }

    suspend fun cancel(key: ChapterBlobKey, now: Long) {
        blobs.delete(key)
        repository.save(DownloadRecord(key, DownloadState.CANCELLED, updatedAtEpochMillis = now))
    }

    suspend fun run(key: ChapterBlobKey, now: Long): DownloadRunResult {
        val existing = repository.find(key) ?: return DownloadRunResult.FAILURE
        if (existing.state == DownloadState.COMPLETED) return DownloadRunResult.COMPLETED
        if (existing.state == DownloadState.CANCELLED) return DownloadRunResult.CANCELLED
        repository.save(existing.copy(state = DownloadState.RUNNING, attempt = existing.attempt + 1, failureReason = null, updatedAtEpochMillis = now))
        return try {
            when (val fetched = source.fetch(key)) {
                is DownloadFetchResult.Success -> complete(key, fetched, now, existing.attempt + 1)
                is DownloadFetchResult.Failure -> fail(key, fetched.code, fetched.retryable, now, existing.attempt + 1)
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { cancel(key, now) }
            throw cancelled
        }
    }

    private suspend fun complete(key: ChapterBlobKey, fetched: DownloadFetchResult.Success, now: Long, attempt: Int): DownloadRunResult =
        try {
            val blob = ChapterBlob.verified(fetched.bytes, fetched.checksum)
            blobs.write(key, blob)
            repository.save(DownloadRecord(key, DownloadState.COMPLETED, blob.checksum, fetched.bytes.size.toLong(), attempt = attempt, updatedAtEpochMillis = now))
            DownloadRunResult.COMPLETED
        } catch (_: IllegalArgumentException) {
            blobs.delete(key)
            repository.save(DownloadRecord(key, DownloadState.FAILED, failureReason = "download.checksum_mismatch", attempt = attempt, updatedAtEpochMillis = now))
            DownloadRunResult.FAILURE
        }

    private suspend fun fail(key: ChapterBlobKey, code: String, retryable: Boolean, now: Long, attempt: Int): DownloadRunResult {
        blobs.delete(key)
        repository.save(DownloadRecord(key, DownloadState.FAILED, failureReason = code, attempt = attempt, updatedAtEpochMillis = now))
        return if (retryable) DownloadRunResult.RETRY else DownloadRunResult.FAILURE
    }
}
