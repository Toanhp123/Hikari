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
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class WorkManagerDownloadScheduler(private val context: Context) : DownloadScheduler {
    override fun schedule(key: ChapterBlobKey) {
        val request = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(ChapterDownloadWorker.RELEASE_ID to key.releaseId.value, ChapterDownloadWorker.FINGERPRINT to key.contentFingerprint))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("chapter-download:${key.releaseId.value}:${key.contentFingerprint}", ExistingWorkPolicy.KEEP, request)
    }
}

class ChapterDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val service = EntryPointAccessors.fromApplication(applicationContext, ChapterDownloadWorkerEntryPoint::class.java).service()
        return when (runChapterDownloadWork(inputData.getString(RELEASE_ID), inputData.getString(FINGERPRINT)) { service.run(it, System.currentTimeMillis()) }) {
            ChapterDownloadWorkDecision.SUCCESS -> Result.success()
            ChapterDownloadWorkDecision.RETRY -> Result.retry()
            ChapterDownloadWorkDecision.FAILURE -> Result.failure()
        }
    }

    companion object { const val RELEASE_ID = "release_id"; const val FINGERPRINT = "fingerprint" }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChapterDownloadWorkerEntryPoint { fun service(): DownloadService }

internal enum class ChapterDownloadWorkDecision { SUCCESS, RETRY, FAILURE }

internal suspend fun runChapterDownloadWork(
    releaseId: String?,
    fingerprint: String?,
    run: suspend (ChapterBlobKey) -> DownloadRunResult,
): ChapterDownloadWorkDecision {
    val id = releaseId?.let { runCatching { ChapterReleaseId(it) }.getOrNull() }
        ?: return ChapterDownloadWorkDecision.FAILURE
    if (fingerprint.isNullOrBlank()) return ChapterDownloadWorkDecision.FAILURE
    val key = ChapterBlobKey(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, id, fingerprint)
    return when (run(key)) {
        DownloadRunResult.COMPLETED, DownloadRunResult.CANCELLED -> ChapterDownloadWorkDecision.SUCCESS
        DownloadRunResult.RETRY -> ChapterDownloadWorkDecision.RETRY
        DownloadRunResult.FAILURE -> ChapterDownloadWorkDecision.FAILURE
    }
}
