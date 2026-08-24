package app.openstory.chapters.maintenance

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class ChapterReaggregationServiceTest {
    @Test
    fun reaggregationUsesOnlyPersistedReleaseGraphAndCommitsNoFetchedRows() = runTest {
        val storyId = StoryId("story:merged")
        val release = ChapterRelease(
            id = ChapterReleaseId("release:1"),
            storyId = storyId,
            pluginId = PluginId("content:test"),
            sourceStoryId = "source:story",
            sourceReleaseId = "1",
            displayLabel = "Chapter 1",
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, BigDecimal.ONE, null, null),
            languageTag = "en",
            publishedAtEpochMillis = null,
            canonicalChapterId = null,
        )
        val repository = RecordingChapterRepository(
            ChapterGraphSnapshot(chapters = emptyList(), releases = listOf(release), overrides = emptyList()),
        )

        val result = ChapterReaggregationService(repository).reaggregate(storyId)

        assertEquals(ChapterCommitResult.Success, result)
        val mutation = requireNotNull(repository.committed)
        assertTrue(mutation.releases.isEmpty())
        assertEquals(setOf(release.id), mutation.plan.links.mapTo(linkedSetOf()) { it.releaseId })
    }
}

private class RecordingChapterRepository(
    private val graph: ChapterGraphSnapshot,
) : ChapterRepository {
    var committed: ChapterMutation? = null

    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = flowOf(emptyList())
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> = flowOf(emptyList())
    override suspend fun snapshot(storyId: StoryId): ChapterGraphSnapshot = graph
    override suspend fun commit(mutation: ChapterMutation): ChapterCommitResult {
        committed = mutation
        return ChapterCommitResult.Success
    }
    override suspend fun saveOverride(
        storyId: StoryId,
        override: app.openstory.chapters.model.ChapterAggregationOverride,
    ) = Unit
    override suspend fun syncState(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
    ): ChapterSyncState? = null
}
