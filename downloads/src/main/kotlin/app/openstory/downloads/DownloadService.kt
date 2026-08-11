package app.openstory.downloads

import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.reconcile.StorageWriteAdmission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class DownloadService(
    private val repository: DownloadRepository,
    private val blobs: ChapterBlobStore,
    private val source: DownloadContentSource,
    private val writeAdmission: StorageWriteAdmission = StorageWriteAdmission.ALLOW_ALL,
) {
    suspend fun queue(releaseId: ChapterReleaseId, now: Long) {
        val existing = repository.find(releaseId)
        if (existing?.state != DownloadState.COMPLETED) {
            repository.save(
                DownloadRecord(
                    pendingKey(releaseId),
                    DownloadState.QUEUED,
                    attempt = existing?.attempt ?: 0,
                    updatedAtEpochMillis = now,
                ),
            )
        }
    }

    suspend fun cancel(releaseId: ChapterReleaseId, now: Long) {
        val existing = repository.find(releaseId)
        existing?.takeIf { it.state == DownloadState.COMPLETED }?.let { blobs.delete(it.key) }
        repository.save(
            DownloadRecord(
                pendingKey(releaseId),
                DownloadState.CANCELLED,
                updatedAtEpochMillis = now,
            ),
        )
    }

    suspend fun run(releaseId: ChapterReleaseId, now: Long): DownloadRunResult {
        val existing = repository.find(releaseId) ?: return DownloadRunResult.FAILURE
        return when (existing.state) {
            DownloadState.COMPLETED -> DownloadRunResult.COMPLETED
            DownloadState.CANCELLED -> DownloadRunResult.CANCELLED
            else -> runPending(releaseId, existing, now)
        }
    }

    private suspend fun runPending(
        releaseId: ChapterReleaseId,
        existing: DownloadRecord,
        now: Long,
    ): DownloadRunResult {
        val attempt = existing.attempt + 1
        repository.save(
            existing.copy(
                state = DownloadState.RUNNING,
                attempt = attempt,
                failureReason = null,
                updatedAtEpochMillis = now,
            ),
        )
        return try {
            when (val fetched = source.fetch(releaseId)) {
                is DownloadFetchResult.Success -> complete(releaseId, fetched, now, attempt)
                is DownloadFetchResult.Failure -> fail(
                    releaseId,
                    fetched.code,
                    fetched.retryable,
                    now,
                    attempt,
                )
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { cancel(releaseId, now) }
            throw cancelled
        }
    }

    private suspend fun complete(
        releaseId: ChapterReleaseId,
        fetched: DownloadFetchResult.Success,
        now: Long,
        attempt: Int,
    ): DownloadRunResult {
        if (!writeAdmission.canStore(fetched.bytes.size.toLong())) {
            return fail(
                releaseId = releaseId,
                code = LOW_STORAGE_REASON,
                retryable = true,
                now = now,
                attempt = attempt,
            )
        }
        val key = ChapterBlobKey(
            ChapterBlobNamespace.EXPLICIT_DOWNLOAD,
            releaseId,
            fetched.fingerprint,
        )
        return try {
            val blob = ChapterBlob.verified(fetched.bytes, fetched.checksum)
            blobs.write(key, blob)
            repository.save(
                DownloadRecord(
                    key,
                    DownloadState.COMPLETED,
                    blob.checksum,
                    fetched.bytes.size.toLong(),
                    attempt = attempt,
                    updatedAtEpochMillis = now,
                ),
            )
            DownloadRunResult.COMPLETED
        } catch (_: IllegalArgumentException) {
            blobs.delete(key)
            repository.save(
                DownloadRecord(
                    pendingKey(releaseId),
                    DownloadState.FAILED,
                    failureReason = "download.checksum_mismatch",
                    attempt = attempt,
                    updatedAtEpochMillis = now,
                ),
            )
            DownloadRunResult.FAILURE
        }
    }

    private suspend fun fail(
        releaseId: ChapterReleaseId,
        code: String,
        retryable: Boolean,
        now: Long,
        attempt: Int,
    ): DownloadRunResult {
        repository.save(
            DownloadRecord(
                pendingKey(releaseId),
                DownloadState.FAILED,
                failureReason = code,
                attempt = attempt,
                updatedAtEpochMillis = now,
            ),
        )
        return if (retryable) DownloadRunResult.RETRY else DownloadRunResult.FAILURE
    }

    private fun pendingKey(releaseId: ChapterReleaseId) =
        ChapterBlobKey(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, releaseId, PENDING_FINGERPRINT)

    private companion object {
        const val PENDING_FINGERPRINT = "pending"
        const val LOW_STORAGE_REASON = "download.low_storage"
    }
}
