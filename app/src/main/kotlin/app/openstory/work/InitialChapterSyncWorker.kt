package app.openstory.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.openstory.chapters.sync.ChapterSyncReport
import app.openstory.chapters.sync.ChapterSyncService
import app.openstory.chapters.sync.InitialChapterSyncScheduler
import app.openstory.common.id.StoryId
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class WorkManagerInitialChapterSyncScheduler(
    private val context: Context,
) : InitialChapterSyncScheduler {
    override fun schedule(storyId: StoryId) {
        try {
            val request = OneTimeWorkRequestBuilder<InitialChapterSyncWorker>()
                .setConstraints(WorkConstraintsFactory.networkConnected())
                .setInputData(workDataOf(WorkInput.STORY_ID to storyId.value))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueInitialChapterSyncWorkName(storyId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        } catch (_: RuntimeException) {
            // Mapping approval remains valid if platform scheduling is temporarily unavailable.
        }
    }
}

class InitialChapterSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(
            applicationContext,
            InitialChapterSyncWorkerEntryPoint::class.java,
        ).chapterSyncService()
        return when (runInitialChapterSyncWork(inputData.getString(WorkInput.STORY_ID), sync::sync)) {
            InitialChapterSyncWorkDecision.SUCCESS -> Result.success()
            InitialChapterSyncWorkDecision.RETRY -> Result.retry()
            InitialChapterSyncWorkDecision.FAILURE -> Result.failure()
        }
    }

}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InitialChapterSyncWorkerEntryPoint {
    fun chapterSyncService(): ChapterSyncService
}

internal enum class InitialChapterSyncWorkDecision {
    SUCCESS,
    RETRY,
    FAILURE,
}

internal suspend fun runInitialChapterSyncWork(
    storyIdValue: String?,
    sync: suspend (StoryId) -> ChapterSyncReport,
): InitialChapterSyncWorkDecision {
    val storyId = WorkInput.storyId(storyIdValue).getOrNull()
        ?: return InitialChapterSyncWorkDecision.FAILURE
    return try {
        when (val report = sync(storyId)) {
            is ChapterSyncReport.Success -> InitialChapterSyncWorkDecision.SUCCESS
            is ChapterSyncReport.Failure -> when {
                report.failures.any { failure -> failure.pluginId == null && !failure.retryable } ->
                    InitialChapterSyncWorkDecision.FAILURE

                report.failures.any { failure -> failure.retryable } -> InitialChapterSyncWorkDecision.RETRY
                else -> InitialChapterSyncWorkDecision.SUCCESS
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        InitialChapterSyncWorkDecision.RETRY
    }
}

internal fun uniqueInitialChapterSyncWorkName(storyId: StoryId): String =
    WorkNames.storyChapterSync(storyId)
