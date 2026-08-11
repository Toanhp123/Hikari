package app.openstory.chapters.repository

import app.openstory.chapters.aggregation.AggregationPlan
import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.StoryId

data class ChapterMutation(
    val storyId: StoryId,
    val releases: List<ChapterRelease>,
    val plan: AggregationPlan,
    val syncState: ChapterSyncState? = null,
)

sealed interface ChapterCommitResult {
    data object Success : ChapterCommitResult

    data class Failure(
        val code: String,
        val retryable: Boolean,
    ) : ChapterCommitResult
}
