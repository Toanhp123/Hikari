package app.openstory.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadRunResult
import app.openstory.downloads.DownloadScheduler
import app.openstory.downloads.DownloadService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class WorkManagerDownloadScheduler(private val context: Context) : DownloadScheduler {
    override fun schedule(releaseId: ChapterReleaseId) {
        try {
            val request = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
                .setConstraints(WorkConstraintsFactory.networkConnected())
                .setInputData(workDataOf(WorkInput.CHAPTER_RELEASE_ID to releaseId.value))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WorkNames.chapterDownload(releaseId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        } catch (_: RuntimeException) {
            // The queued download remains durable and can be scheduled again by the capability owner.
        }
    }

    override fun cancel(releaseId: ChapterReleaseId) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WorkNames.chapterDownload(releaseId))
        } catch (_: RuntimeException) {
            // Capability cancellation remains authoritative if WorkManager is unavailable.
        }
    }
}

class ChapterDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val service = EntryPointAccessors.fromApplication(
            applicationContext,
            ChapterDownloadWorkerEntryPoint::class.java,
        ).service()
        val decision = runChapterDownloadWork(inputData.getString(WorkInput.CHAPTER_RELEASE_ID)) {
            service.run(it, System.currentTimeMillis())
        }
        return when (decision) {
            ChapterDownloadWorkDecision.SUCCESS -> Result.success()
            ChapterDownloadWorkDecision.RETRY -> Result.retry()
            ChapterDownloadWorkDecision.FAILURE -> Result.failure()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChapterDownloadWorkerEntryPoint { fun service(): DownloadService }

internal enum class ChapterDownloadWorkDecision { SUCCESS, RETRY, FAILURE }

internal suspend fun runChapterDownloadWork(
    releaseId: String?,
    run: suspend (ChapterReleaseId) -> DownloadRunResult,
): ChapterDownloadWorkDecision {
    val id = WorkInput.chapterReleaseId(releaseId).getOrNull()
        ?: return ChapterDownloadWorkDecision.FAILURE
    return try {
        when (run(id)) {
            DownloadRunResult.COMPLETED, DownloadRunResult.CANCELLED -> ChapterDownloadWorkDecision.SUCCESS
            DownloadRunResult.RETRY -> ChapterDownloadWorkDecision.RETRY
            DownloadRunResult.FAILURE -> ChapterDownloadWorkDecision.FAILURE
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ChapterDownloadWorkDecision.RETRY
    }
}
