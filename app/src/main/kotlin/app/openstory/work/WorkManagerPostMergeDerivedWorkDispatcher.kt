package app.openstory.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.openstory.catalog.orchestration.PostMergeDerivedRequirements
import app.openstory.catalog.orchestration.PostMergeDerivedWorkDispatcher
import app.openstory.catalog.orchestration.PostMergeDerivedWorkResult
import app.openstory.chapters.maintenance.ChapterReaggregator
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CancellationException

class WorkManagerPostMergeDerivedWorkDispatcher(
    private val context: Context,
    private val chapters: ChapterReaggregator,
) : PostMergeDerivedWorkDispatcher {
    override suspend fun dispatch(
        storyId: StoryId,
        requirements: PostMergeDerivedRequirements,
    ): PostMergeDerivedWorkResult = dispatchPostMergeDerivedWork(
        storyId = storyId,
        requirements = requirements,
        reaggregate = chapters::reaggregate,
        scheduleNetworkWork = ::scheduleNetworkWork,
    )

    private fun scheduleNetworkWork(
        storyId: StoryId,
        recomputeMappings: Boolean,
        refreshChapterSync: Boolean,
    ) {
        val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val mapping = if (recomputeMappings) {
            OneTimeWorkRequestBuilder<LibraryMappingWorker>()
                .setConstraints(network)
                .setInputData(workDataOf(LibraryMappingWorker.STORY_ID_KEY to storyId.value))
                .build()
        } else {
            null
        }
        val chapterSync = if (refreshChapterSync) {
            OneTimeWorkRequestBuilder<InitialChapterSyncWorker>()
                .setConstraints(network)
                .setInputData(workDataOf(WorkInput.STORY_ID to storyId.value))
                .build()
        } else {
            null
        }
        enqueueNetworkWork(storyId, mapping, chapterSync)
    }

    private fun enqueueNetworkWork(
        storyId: StoryId,
        mapping: OneTimeWorkRequest?,
        chapterSync: OneTimeWorkRequest?,
    ) {
        val manager = WorkManager.getInstance(context)
        when {
            mapping != null && chapterSync != null -> manager
                .beginUniqueWork(postMergeDerivedWorkName(storyId), ExistingWorkPolicy.APPEND_OR_REPLACE, mapping)
                .then(chapterSync)
                .enqueue()

            mapping != null -> manager.enqueueUniqueWork(
                postMergeDerivedWorkName(storyId),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                mapping,
            )

            chapterSync != null -> manager.enqueueUniqueWork(
                postMergeDerivedWorkName(storyId),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                chapterSync,
            )

            else -> Unit
        }
    }
}

internal suspend fun dispatchPostMergeDerivedWork(
    storyId: StoryId,
    requirements: PostMergeDerivedRequirements,
    reaggregate: suspend (StoryId) -> ChapterCommitResult,
    scheduleNetworkWork: (StoryId, Boolean, Boolean) -> Unit,
): PostMergeDerivedWorkResult {
    val reaggregationFailure = if (requirements.reaggregateChapters) {
        reaggregatePostMergeChapters(storyId, reaggregate)
    } else {
        null
    }
    return reaggregationFailure ?: schedulePostMergeNetworkWork(
        storyId = storyId,
        requirements = requirements,
        scheduleNetworkWork = scheduleNetworkWork,
    )
}

private suspend fun reaggregatePostMergeChapters(
    storyId: StoryId,
    reaggregate: suspend (StoryId) -> ChapterCommitResult,
): PostMergeDerivedWorkResult.Failed? = try {
    when (val result = reaggregate(storyId)) {
        is ChapterCommitResult.Failure -> PostMergeDerivedWorkResult.Failed(
            code = result.code,
            retryable = result.retryable,
        )

        else -> null
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    PostMergeDerivedWorkResult.Failed(
        code = "canonical.derived.reaggregation_exception",
        retryable = true,
    )
}

private fun schedulePostMergeNetworkWork(
    storyId: StoryId,
    requirements: PostMergeDerivedRequirements,
    scheduleNetworkWork: (StoryId, Boolean, Boolean) -> Unit,
): PostMergeDerivedWorkResult = if (!requirements.recomputeMappings && !requirements.refreshChapterSync) {
    PostMergeDerivedWorkResult.Dispatched
} else {
    try {
        scheduleNetworkWork(
            storyId,
            requirements.recomputeMappings,
            requirements.refreshChapterSync,
        )
        PostMergeDerivedWorkResult.Dispatched
    } catch (_: RuntimeException) {
        PostMergeDerivedWorkResult.Failed(
            code = "canonical.derived.schedule_failed",
            retryable = true,
        )
    }
}

internal fun postMergeDerivedWorkName(storyId: StoryId): String =
    "canonical-post-merge-derived:${storyId.value}"
