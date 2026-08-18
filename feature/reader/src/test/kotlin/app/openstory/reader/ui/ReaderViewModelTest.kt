package app.openstory.reader.ui

import androidx.lifecycle.SavedStateHandle
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.common.FakeClock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderDocumentRepository
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.selection.ReleaseSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsStableIdsRestoresExactReleaseAndExposesNeighborNavigation() = runTest(dispatcher.scheduler) {
        val chapters = FakeReaderChapterRepository(graph())
        val progress = FakeReaderProgressRepository(
            progress("chapter-2", "release-b", "fingerprint-b"),
        )
        val savedState = SavedStateHandle()
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter-2", null),
            savedState,
            chapters,
            documents(),
            progress,
            FakeClock(100),
        )
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals("release-b", viewModel.state.value.selectedReleaseId?.value)
        assertEquals("chapter-1", viewModel.state.value.previousChapterId?.value)
        assertEquals(null, viewModel.state.value.nextChapterId)
        assertEquals("block", viewModel.state.value.restoredBlockId)
        assertEquals(0.5f, viewModel.state.value.restoredProgressFraction)

        viewModel.increaseFont()
        assertEquals(1.1f, savedState.get<Float>("reader.font-scale"))
    }

    @Test
    fun nextChapterReloadsInsideTheSameViewModelAndPersistsCurrentChapter() = runTest(dispatcher.scheduler) {
        val savedState = SavedStateHandle()
        val chapters = FakeReaderChapterRepository(graph())
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter-1", null),
            savedState,
            chapters,
            documents(),
            FakeReaderProgressRepository(null),
            FakeClock(100),
        )
        runCurrent()

        viewModel.openChapter(CanonicalChapterId("chapter-2"))
        runCurrent()

        assertEquals("chapter-2", viewModel.state.value.chapterLabel)
        assertEquals("chapter-1", viewModel.state.value.previousChapterId?.value)
        assertEquals(null, viewModel.state.value.nextChapterId)
        assertEquals("chapter-2", savedState.get<String>("reader.chapter-id"))
        assertEquals(1, chapters.snapshotCalls)
    }

    @Test
    fun chapterSwitchFlushesPendingProgressForThePreviousChapter() = runTest(dispatcher.scheduler) {
        val progress = FakeReaderProgressRepository(null)
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter-1", null),
            SavedStateHandle(),
            FakeReaderChapterRepository(graph()),
            documents(),
            progress,
            FakeClock(100),
        )
        runCurrent()

        viewModel.updatePosition(ReadingPosition("block", 3, 0.25f), completed = false)
        viewModel.openChapter(CanonicalChapterId("chapter-2"))
        runCurrent()

        assertEquals("chapter-1", progress.current()?.canonicalChapterId?.value)
        assertEquals("chapter-2", viewModel.state.value.chapterLabel)
    }

    @Test
    fun chapterGraphGroupingPreservesChapterAndReleaseOrder() {
        val first = chapter("chapter-1", "release-a")
        val firstAlternate = first.second.copy(
            id = ChapterReleaseId("release-a-2"),
            sourceReleaseId = "release-a-2",
        )
        val second = chapter("chapter-2", "release-b")
        val groups = ChapterGraphSnapshot(
            chapters = listOf(first.first, second.first),
            releases = listOf(first.second, second.second, firstAlternate),
            overrides = emptyList(),
        ).toReaderGroups()

        assertEquals(listOf("chapter-1", "chapter-2"), groups.map { it.chapter.id.value })
        assertEquals(
            listOf("release-a", "release-a-2"),
            groups.first().releases.map { it.id.value },
        )
        assertEquals(listOf("release-b"), groups.last().releases.map { it.id.value })
    }

    @Test
    fun explicitSourceSwitchIsSavedAndReloaded() = runTest(dispatcher.scheduler) {
        val first = chapter("chapter", "release-a")
        val secondRelease = first.second.copy(id = ChapterReleaseId("release-b"), sourceReleaseId = "release-b")
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter", "release-a"),
            SavedStateHandle(),
            FakeReaderChapterRepository(
                ChapterGraphSnapshot(listOf(first.first), listOf(first.second, secondRelease), emptyList()),
            ),
            documents(),
            FakeReaderProgressRepository(null),
            FakeClock(100),
        )
        runCurrent()

        viewModel.selectRelease(ChapterReleaseId("release-b"))
        runCurrent()

        assertEquals("release-b", viewModel.state.value.selectedReleaseId?.value)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun restoredSourceSelectionOverridesTheOriginalRouteRelease() = runTest(dispatcher.scheduler) {
        val first = chapter("chapter", "release-a")
        val secondRelease = first.second.copy(id = ChapterReleaseId("release-b"), sourceReleaseId = "release-b")
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter", "release-a"),
            SavedStateHandle(mapOf("reader.release-id" to "release-b")),
            FakeReaderChapterRepository(
                ChapterGraphSnapshot(listOf(first.first), listOf(first.second, secondRelease), emptyList()),
            ),
            documents(),
            FakeReaderProgressRepository(null),
            FakeClock(100),
        )
        runCurrent()

        assertEquals("release-b", viewModel.state.value.selectedReleaseId?.value)
    }

    @Test
    fun flushStartsPersistenceBeforeNavigationCanClearTheViewModel() = runTest(dispatcher.scheduler) {
        val repository = FakeReaderProgressRepository(null)
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter", "release-a"),
            SavedStateHandle(),
            FakeReaderChapterRepository(graphForSingleChapter()),
            documents(),
            repository,
            FakeClock(100),
        )
        runCurrent()
        viewModel.updatePosition(ReadingPosition("block", 4, 0.4f), completed = false)

        viewModel.flushProgress()

        assertEquals(ReadingPosition("block", 4, 0.4f), repository.current()?.position)
    }

    private fun documents() = ReaderDocumentRepository(
        NoOpReaderDocumentStore,
        object : ReaderDocumentSourceRegistry {
            override suspend fun enabled(): List<ReaderDocumentSource> = listOf(
                object : ReaderDocumentSource {
                    override val pluginId = PluginId("plugin")
                    override suspend fun fetch(release: ChapterRelease) = ReaderSourceResult.Success(
                        ReaderDocument(
                            release.displayLabel,
                            listOf(ReaderBlock.Paragraph("block", "Text")),
                            "fingerprint-${release.id.value.removePrefix("release-")}",
                        ),
                    )
                },
            )
        },
        ReleaseSelector(),
    )
}

private class FakeReaderChapterRepository(
    private val graph: ChapterGraphSnapshot,
) : ChapterRepository {
    private val all = MutableStateFlow<List<CanonicalChapterGroup>>(emptyList())
    var snapshotCalls = 0
    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = all
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> =
        error("Not used")
    override suspend fun snapshot(storyId: StoryId): ChapterGraphSnapshot {
        snapshotCalls++
        return graph
    }
    override suspend fun commit(mutation: ChapterMutation): ChapterCommitResult = ChapterCommitResult.Success
    override suspend fun saveOverride(storyId: StoryId, override: ChapterAggregationOverride) = Unit
    override suspend fun syncState(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
    ): ChapterSyncState? = null
}

private class FakeReaderProgressRepository(initial: ReadingProgress?) : ReadingProgressRepository {
    private val value = MutableStateFlow(initial)
    private val all = MutableStateFlow(initial?.let(::listOf).orEmpty())
    override fun observeAll(): Flow<List<ReadingProgress>> = all
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = value
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId) = value.value
    override suspend fun save(progress: ReadingProgress) {
        value.value = progress
        all.value = listOf(progress)
    }
    fun current(): ReadingProgress? = value.value
}

private fun graph(): ChapterGraphSnapshot {
    val first = chapter("chapter-1", "release-a")
    val second = chapter("chapter-2", "release-b")
    return ChapterGraphSnapshot(
        listOf(first.first, second.first),
        listOf(first.second, second.second),
        emptyList(),
    )
}

private fun graphForSingleChapter(): ChapterGraphSnapshot {
    val chapter = chapter("chapter", "release-a")
    return ChapterGraphSnapshot(listOf(chapter.first), listOf(chapter.second), emptyList())
}

private fun chapter(chapterId: String, releaseId: String): Pair<CanonicalChapter, ChapterRelease> {
    val id = CanonicalChapterId(chapterId)
    val label = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null)
    val release = ChapterRelease(
        ChapterReleaseId(releaseId), StoryId("story"), PluginId("plugin"), "source-story", releaseId,
        chapterId, label, "en", 1, id,
    )
    return CanonicalChapter(id, StoryId("story"), label, chapterId, false, setOf(release.id)) to release
}

private fun progress(chapterId: String, releaseId: String, fingerprint: String) = ReadingProgress(
    StoryId("story"), CanonicalChapterId(chapterId), ChapterReleaseId(releaseId), fingerprint,
    app.openstory.reader.progress.ReadingPosition("block", 5, 0.5f), null, 10,
)

private object NoOpReaderDocumentStore : ReaderDocumentStore {
    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? = null
    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? = null
    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) = Unit
    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) = Unit
}
