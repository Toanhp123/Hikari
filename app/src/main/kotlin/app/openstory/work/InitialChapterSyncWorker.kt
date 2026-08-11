package app.openstory.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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

class WorkManagerInitialChapterSyncScheduler(
    private val context: Context,
) : InitialChapterSyncScheduler {
    override fun schedule(storyId: StoryId) {
        try {
            val request = OneTimeWorkRequestBuilder<InitialChapterSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(workDataOf(InitialChapterSyncWorker.STORY_ID_KEY to storyId.value))
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
        return when (runInitialChapterSyncWork(inputData.getString(STORY_ID_KEY), sync::sync)) {
            InitialChapterSyncWorkDecision.SUCCESS -> Result.success()
            InitialChapterSyncWorkDecision.RETRY -> Result.retry()
            InitialChapterSyncWorkDecision.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val STORY_ID_KEY = "story_id"
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
    val storyId = storyIdValue?.let { value -> runCatching { StoryId(value) }.getOrNull() }
        ?: return InitialChapterSyncWorkDecision.FAILURE
    return when (val report = sync(storyId)) {
        is ChapterSyncReport.Success -> InitialChapterSyncWorkDecision.SUCCESS
        is ChapterSyncReport.Failure -> if (report.failures.any { failure -> failure.retryable }) {
            InitialChapterSyncWorkDecision.RETRY
        } else {
            InitialChapterSyncWorkDecision.FAILURE
        }
    }
}

internal fun uniqueInitialChapterSyncWorkName(storyId: StoryId): String =
    "initial-chapter-sync:${storyId.value}"
