package app.openstory.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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

class WorkManagerDownloadScheduler(private val context: Context) : DownloadScheduler {
    private fun workName(releaseId: ChapterReleaseId) = "chapter-download:${releaseId.value}"

    override fun schedule(releaseId: ChapterReleaseId) {
        val request = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(ChapterDownloadWorker.RELEASE_ID to releaseId.value))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(releaseId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(releaseId: ChapterReleaseId) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(releaseId))
    }
}

class ChapterDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val service = EntryPointAccessors.fromApplication(
            applicationContext,
            ChapterDownloadWorkerEntryPoint::class.java,
        ).service()
        val decision = runChapterDownloadWork(inputData.getString(RELEASE_ID)) {
            service.run(it, System.currentTimeMillis())
        }
        return when (decision) {
            ChapterDownloadWorkDecision.SUCCESS -> Result.success()
            ChapterDownloadWorkDecision.RETRY -> Result.retry()
            ChapterDownloadWorkDecision.FAILURE -> Result.failure()
        }
    }

    companion object { const val RELEASE_ID = "release_id" }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChapterDownloadWorkerEntryPoint { fun service(): DownloadService }

internal enum class ChapterDownloadWorkDecision { SUCCESS, RETRY, FAILURE }

internal suspend fun runChapterDownloadWork(
    releaseId: String?,
    run: suspend (ChapterReleaseId) -> DownloadRunResult,
): ChapterDownloadWorkDecision {
    val id = releaseId?.let { runCatching { ChapterReleaseId(it) }.getOrNull() }
        ?: return ChapterDownloadWorkDecision.FAILURE
    return when (run(id)) {
        DownloadRunResult.COMPLETED, DownloadRunResult.CANCELLED -> ChapterDownloadWorkDecision.SUCCESS
        DownloadRunResult.RETRY -> ChapterDownloadWorkDecision.RETRY
        DownloadRunResult.FAILURE -> ChapterDownloadWorkDecision.FAILURE
    }
}
