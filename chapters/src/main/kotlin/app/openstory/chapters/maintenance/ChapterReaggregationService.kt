package app.openstory.chapters.maintenance

import app.openstory.chapters.aggregation.ChapterAggregationEngine
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.id.StoryId

fun interface ChapterReaggregator {
    suspend fun reaggregate(storyId: StoryId): ChapterCommitResult
}

class ChapterReaggregationService(
    private val chapters: ChapterRepository,
    private val aggregation: ChapterAggregationEngine = ChapterAggregationEngine(),
) : ChapterReaggregator {
    override suspend fun reaggregate(storyId: StoryId): ChapterCommitResult {
        val graph = chapters.snapshot(storyId)
        val plan = aggregation.plan(
            storyId = storyId,
            existing = graph.chapters,
            releases = graph.releases,
            overrides = graph.overrides,
        )
        return chapters.commit(
            ChapterMutation(
                storyId = storyId,
                releases = emptyList(),
                plan = plan,
            ),
        )
    }
}
