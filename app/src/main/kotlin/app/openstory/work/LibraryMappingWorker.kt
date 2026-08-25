package app.openstory.work

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryMappingScheduler
import app.openstory.library.mapping.ContentMappingSearchReport
import app.openstory.library.mapping.ContentMappingService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class WorkManagerLibraryMappingScheduler(
    private val context: Context,
) : LibraryMappingScheduler {
    override fun schedule(storyId: StoryId) {
        try {
            val request = OneTimeWorkRequestBuilder<LibraryMappingWorker>()
                .setConstraints(WorkConstraintsFactory.networkConnected())
                .setInputData(workDataOf(WorkInput.STORY_ID to storyId.value))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WorkNames.libraryMapping(storyId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        } catch (_: RuntimeException) {
            // Library membership remains valid even if platform scheduling is unavailable.
        }
    }
}

class LibraryMappingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val service = EntryPointAccessors.fromApplication(
            applicationContext,
            LibraryMappingWorkerEntryPoint::class.java,
        ).mappingService()
        return when (runLibraryMappingWork(inputData.getString(WorkInput.STORY_ID), service::automate)) {
            LibraryMappingWorkDecision.SUCCESS -> Result.success()
            LibraryMappingWorkDecision.RETRY -> Result.retry()
            LibraryMappingWorkDecision.FAILURE -> Result.failure()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryMappingWorkerEntryPoint {
    fun mappingService(): ContentMappingService
}

internal enum class LibraryMappingWorkDecision {
    SUCCESS,
    RETRY,
    FAILURE,
}

internal suspend fun runLibraryMappingWork(
    storyIdValue: String?,
    search: suspend (StoryId) -> ContentMappingSearchReport,
): LibraryMappingWorkDecision {
    val storyId = WorkInput.storyId(storyIdValue).getOrNull()
    return if (storyId == null) {
        LibraryMappingWorkDecision.FAILURE
    } else {
        try {
            decideMappingWork(search(storyId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LibraryMappingWorkDecision.RETRY
        }
    }
}

private fun decideMappingWork(report: ContentMappingSearchReport): LibraryMappingWorkDecision {
    val globalFailure = report.failures.any { failure -> failure.pluginId == null }
    val failedPluginIds = report.failures.mapNotNull { failure -> failure.pluginId }.toSet()
    val everySearchedSourceFailed = report.searchedPluginIds.isNotEmpty() &&
        failedPluginIds == report.searchedPluginIds.toSet()
    val retryableExhaustion = report.candidates.isEmpty() &&
        everySearchedSourceFailed &&
        report.failures.any { failure -> failure.retryable }
    return when {
        globalFailure -> if (report.failures.any { it.retryable }) {
            LibraryMappingWorkDecision.RETRY
        } else {
            LibraryMappingWorkDecision.FAILURE
        }
        retryableExhaustion -> LibraryMappingWorkDecision.RETRY
        else -> LibraryMappingWorkDecision.SUCCESS
    }
}

internal fun uniqueWorkName(storyId: StoryId): String = WorkNames.libraryMapping(storyId)
