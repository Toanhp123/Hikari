package app.openstory.catalog.ui.chapters

import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterOverrideKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun projectsCanonicalUnreadCountExpansionAndFilters() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1", releaseCount = 2), group("2")))
        val viewModel = ChapterListViewModel(ChapterListAssistedArgs(STORY_ID), repository)
        runCurrent()

        assertEquals(2, viewModel.state.value.unreadCount)
        assertFalse(viewModel.state.value.chapters.first().expanded)

        viewModel.toggleExpanded(CanonicalChapterId("chapter:1"))
        viewModel.selectFilter(ChapterListFilter.MULTI_RELEASE)
        runCurrent()

        assertEquals(listOf(CanonicalChapterId("chapter:1")), viewModel.state.value.chapters.map { it.id })
        assertEquals(2, viewModel.state.value.chapters.single().releases.size)
        assertTrue(viewModel.state.value.chapters.single().expanded)
    }

    @Test
    fun tombstonesStayHiddenUntilExplicitlyRequested() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1"), group("2", tombstoned = true)))
        val viewModel = ChapterListViewModel(ChapterListAssistedArgs(STORY_ID), repository)
        runCurrent()

        assertEquals(listOf(CanonicalChapterId("chapter:1")), viewModel.state.value.chapters.map { it.id })

        viewModel.setTombstonesVisible(true)
        runCurrent()

        assertEquals(2, viewModel.state.value.chapters.size)
    }

    @Test
    fun correctionCommandsPersistProtectedOverrides() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1")))
        val viewModel = ChapterListViewModel(ChapterListAssistedArgs(STORY_ID), repository)
        runCurrent()
        val releaseId = ChapterReleaseId("release:1:0")
        val chapterId = CanonicalChapterId("chapter:1")

        viewModel.keepGrouped(releaseId, chapterId)
        viewModel.separate(releaseId)
        runCurrent()

        assertEquals(
            listOf(
                ChapterAggregationOverride(releaseId, chapterId, ChapterOverrideKind.FORCE_LINK),
                ChapterAggregationOverride(releaseId, null, ChapterOverrideKind.FORCE_SEPARATE),
            ),
            repository.savedOverrides,
        )
    }
}

private class FakeChapterRepository(initial: List<CanonicalChapterGroup>) : ChapterRepository {
    private val groups = MutableStateFlow(initial)
    val savedOverrides = mutableListOf<ChapterAggregationOverride>()

    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = groups
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> = groups
    override suspend fun snapshot(storyId: StoryId) = ChapterGraphSnapshot(emptyList(), emptyList(), emptyList())
    override suspend fun commit(mutation: ChapterMutation): ChapterCommitResult = ChapterCommitResult.Success
    override suspend fun saveOverride(storyId: StoryId, override: ChapterAggregationOverride) {
        savedOverrides += override
    }
    override suspend fun syncState(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
    ): ChapterSyncState? = null
}

private fun group(
    number: String,
    releaseCount: Int = 1,
    tombstoned: Boolean = false,
): CanonicalChapterGroup {
    val chapterId = CanonicalChapterId("chapter:$number")
    val label = ParsedChapterLabel(ChapterKind.NUMBERED, null, BigDecimal(number), null, null)
    val releases = (0 until releaseCount).map { index ->
        ChapterRelease(
            id = ChapterReleaseId("release:$number:$index"),
            storyId = STORY_ID,
            pluginId = PluginId("content.$index"),
            sourceStoryId = "source-story",
            sourceReleaseId = "source-$number-$index",
            displayLabel = "Chapter $number",
            parsedLabel = label,
            languageTag = "en",
            publishedAtEpochMillis = index.toLong(),
            canonicalChapterId = chapterId,
        )
    }
    return CanonicalChapterGroup(
        CanonicalChapter(chapterId, STORY_ID, label, "Chapter $number", tombstoned, releases.mapTo(linkedSetOf()) { it.id }),
        releases,
    )
}

private val STORY_ID = StoryId("story:chapters-ui")
