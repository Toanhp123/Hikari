package app.openstory.downloads

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.reconcile.StorageWriteAdmission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DownloadService(
    private val repository: DownloadRepository,
    private val blobs: ChapterBlobStore,
    private val source: DownloadContentSource,
    private val writeAdmission: StorageWriteAdmission = StorageWriteAdmission.ALLOW_ALL,
) {
    fun observe(releaseId: ChapterReleaseId): Flow<DownloadRecord?> = repository.observe(releaseId)

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
                is DownloadFetchResult.Success -> {
                    if (repository.find(releaseId)?.state == DownloadState.CANCELLED) {
                        DownloadRunResult.CANCELLED
                    } else {
                        complete(releaseId, fetched, now, attempt)
                    }
                }
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
            val cancelledAfterWrite = repository.find(releaseId)?.state == DownloadState.CANCELLED
            if (cancelledAfterWrite) {
                blobs.delete(key)
                DownloadRunResult.CANCELLED
            } else {
                val completed = repository.completeUnlessCancelled(
                    DownloadRecord(
                        key = key,
                        state = DownloadState.COMPLETED,
                        checksum = blob.checksum,
                        sizeBytes = fetched.bytes.size.toLong(),
                        attempt = attempt,
                        updatedAtEpochMillis = now,
                    ),
                )
                if (completed) {
                    DownloadRunResult.COMPLETED
                } else {
                    blobs.delete(key)
                    DownloadRunResult.CANCELLED
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            deleteBestEffort(key)
            fail(releaseId, "download.checksum_mismatch", false, now, attempt)
        } catch (_: Exception) {
            deleteBestEffort(key)
            fail(releaseId, STORAGE_IO_REASON, true, now, attempt)
        }
    }

    private suspend fun deleteBestEffort(key: ChapterBlobKey) {
        try {
            blobs.delete(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Reconciliation removes any partial blob that cannot be deleted immediately.
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
        const val STORAGE_IO_REASON = "download.storage_io"
    }
}
