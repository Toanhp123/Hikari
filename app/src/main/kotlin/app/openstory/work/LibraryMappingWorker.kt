package app.openstory.work

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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

class WorkManagerLibraryMappingScheduler(
    private val context: Context,
) : LibraryMappingScheduler {
    override fun schedule(storyId: StoryId) {
        try {
            val request = OneTimeWorkRequestBuilder<LibraryMappingWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(workDataOf(LibraryMappingWorker.STORY_ID_KEY to storyId.value))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(storyId),
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
        return when (runLibraryMappingWork(inputData.getString(STORY_ID_KEY), service::automate)) {
            LibraryMappingWorkDecision.SUCCESS -> Result.success()
            LibraryMappingWorkDecision.RETRY -> Result.retry()
            LibraryMappingWorkDecision.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val STORY_ID_KEY = "story_id"
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
    val storyId = storyIdValue?.let { value -> runCatching { StoryId(value) }.getOrNull() }
    return if (storyId == null) {
        LibraryMappingWorkDecision.FAILURE
    } else {
        decideMappingWork(search(storyId))
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
        globalFailure -> LibraryMappingWorkDecision.FAILURE
        retryableExhaustion -> LibraryMappingWorkDecision.RETRY
        else -> LibraryMappingWorkDecision.SUCCESS
    }
}

internal fun uniqueWorkName(storyId: StoryId): String = "library-mapping:${storyId.value}"
