package app.openstory.reader.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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
import app.openstory.reader.assets.ContentFetchArbiter
import app.openstory.reader.assets.ReaderAssetChapterManifest
import app.openstory.reader.assets.ReaderAssetSessionPort
import app.openstory.reader.assets.ReaderPrefetchedDocumentArtifact
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.preferences.ReaderPreferencesPort
import app.openstory.reader.routing.ContentSourceExecutionLane
import app.openstory.reader.routing.ReaderHalfOpenProbeRegistry
import app.openstory.reader.routing.ReaderRouteCoordinator
import app.openstory.reader.routing.ReaderRouteSessionFactory
import app.openstory.reader.routing.ReaderSessionId
import app.openstory.reader.routing.ReaderSourceHealthRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
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
        val preferenceValues = MutableStateFlow(ReaderPreferences())
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter-2", null),
            savedState,
            chapters,
            documents(progress),
            progress,
            FakeClock(100),
            FakeReaderPreferencesPort(preferenceValues),
        )
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals("release-b", viewModel.state.value.selectedReleaseId?.value)
        assertEquals("chapter-1", viewModel.state.value.previousChapterId?.value)
        assertEquals(null, viewModel.state.value.nextChapterId)
        assertEquals("block", viewModel.state.value.restoredBlockId)
        assertEquals(0.5f, viewModel.state.value.restoredProgressFraction)

        viewModel.increaseFont()
        runCurrent()
        assertEquals(1.1f, preferenceValues.value.fontScale)
        assertEquals(null, savedState.get<Float>("reader.font-scale"))
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
        assertEquals(1, chapters.observeCalls)
        assertEquals(0, chapters.snapshotCalls)
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
    fun nonRetryableReaderSourceFailureIsProjectedWithoutRetry() = runTest(dispatcher.scheduler) {
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter", "release-a"),
            SavedStateHandle(),
            FakeReaderChapterRepository(graphForSingleChapter()),
            failingDocuments("plugin.operation_unavailable", retryable = false),
            FakeReaderProgressRepository(null),
            FakeClock(100),
        )
        runCurrent()

        assertEquals("plugin.operation_unavailable", viewModel.state.value.failure)
        assertFalse(viewModel.state.value.failureRetryable)
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

    @Test
    fun waitsForFirstPreferenceAndUsesItsLanguageOrderForInitialSelection() = runTest(dispatcher.scheduler) {
        val values = MutableSharedFlow<ReaderPreferences>()
        val chapters = FakeReaderChapterRepository(graphWithLanguages())
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter", null),
            SavedStateHandle(),
            chapters,
            documents(),
            FakeReaderProgressRepository(null),
            FakeClock(100),
            FakeReaderPreferencesPort(values),
        )
        runCurrent()
        assertEquals(1, chapters.observeCalls)
        assertEquals(0, chapters.snapshotCalls)

        values.emit(ReaderPreferences(fontScale = 1.2f, languageOrder = listOf("ja", "en")))
        runCurrent()

        assertEquals(1, chapters.observeCalls)
        assertEquals(0, chapters.snapshotCalls)
        assertEquals("release-ja", viewModel.state.value.selectedReleaseId?.value)
        assertEquals(1.2f, viewModel.state.value.fontScale)
    }

    @Test
    fun failedFontPersistenceRestoresLastPersistedValue() = runTest(dispatcher.scheduler) {
        val values = MutableStateFlow(ReaderPreferences(fontScale = 1f, languageOrder = listOf("en")))
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter", null),
            SavedStateHandle(),
            FakeReaderChapterRepository(graphForSingleChapter()),
            documents(),
            FakeReaderProgressRepository(null),
            FakeClock(100),
            FakeReaderPreferencesPort(values, failWrites = true),
        )
        runCurrent()

        viewModel.increaseFont()
        runCurrent()

        assertEquals(1f, viewModel.state.value.fontScale)
        assertEquals("reader.preferences_write_failed", viewModel.state.value.preferenceFailure)
    }

    @Test
    fun onClearedClosesRouteSessionExplicitly() = runTest(dispatcher.scheduler) {
        val assetPort = RecordingReaderAssetSessionPort()
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter", null),
            SavedStateHandle(),
            FakeReaderChapterRepository(graphForSingleChapter()),
            documents(assetSessionPort = assetPort),
            FakeReaderProgressRepository(null),
            FakeClock(100),
        )
        runCurrent()

        ViewModel::class.java.getDeclaredMethod("onCleared").apply {
            isAccessible = true
            invoke(viewModel)
        }

        assertEquals(1, assetPort.releasedSessions.size)
    }
    private fun failingDocuments(
        code: String,
        retryable: Boolean,
        progress: ReadingProgressRepository = FakeReaderProgressRepository(null),
    ) = ReaderRouteSessionFactory(
        ReaderRouteCoordinator(
            store = NoOpReaderDocumentStore,
            sources = object : ReaderDocumentSourceRegistry {
                override suspend fun enabled(): List<ReaderDocumentSource> = listOf(
                    object : ReaderDocumentSource {
                        override val pluginId = PluginId("plugin")
                        override suspend fun fetch(release: ChapterRelease) =
                            ReaderSourceResult.Failure(code, retryable)
                    },
                )
            },
            progress = progress,
            healthRegistry = ReaderSourceHealthRegistry(),
            sourceLane = ContentSourceExecutionLane(),
            fetchArbiter = ContentFetchArbiter(),
            halfOpenProbeRegistry = ReaderHalfOpenProbeRegistry(),
        ),
    )

    private fun documents(
        progress: ReadingProgressRepository = FakeReaderProgressRepository(null),
        assetSessionPort: ReaderAssetSessionPort = ReaderAssetSessionPort.NO_OP,
    ) = ReaderRouteSessionFactory(
        ReaderRouteCoordinator(
            store = NoOpReaderDocumentStore,
            sources = object : ReaderDocumentSourceRegistry {
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
            progress = progress,
            healthRegistry = ReaderSourceHealthRegistry(),
            sourceLane = ContentSourceExecutionLane(),
            fetchArbiter = ContentFetchArbiter(),
            halfOpenProbeRegistry = ReaderHalfOpenProbeRegistry(),
        ),
        assetSessionPort = assetSessionPort,
    )

}

private class RecordingReaderAssetSessionPort : ReaderAssetSessionPort {
    val releasedSessions = mutableListOf<ReaderSessionId>()

    override fun registerCommitted(
        sessionId: ReaderSessionId,
        proposedManifestRevision: Long,
        manifest: ReaderAssetChapterManifest,
    ): Long = proposedManifestRevision

    override fun acceptPrefetchedArtifact(artifact: ReaderPrefetchedDocumentArtifact) = Unit

    override fun releaseSession(sessionId: ReaderSessionId) {
        releasedSessions += sessionId
    }
}

private class FakeReaderChapterRepository(
    private val graph: ChapterGraphSnapshot,
) : ChapterRepository {
    private val all = MutableStateFlow(graph.toReaderGroups())
    var observeCalls = 0
    var snapshotCalls = 0
    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = all
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> {
        observeCalls++
        return all
    }
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

private fun graphWithLanguages(): ChapterGraphSnapshot {
    val base = chapter("chapter", "release-en")
    val japanese = base.second.copy(
        id = ChapterReleaseId("release-ja"),
        sourceReleaseId = "release-ja",
        languageTag = "ja",
    )
    return ChapterGraphSnapshot(listOf(base.first), listOf(base.second, japanese), emptyList())
}

private class FakeReaderPreferencesPort(
    override val preferences: Flow<ReaderPreferences>,
    private val failWrites: Boolean = false,
) : ReaderPreferencesPort {
    override suspend fun setFontScale(value: Float) {
        if (failWrites) error("write failed")
        @Suppress("UNCHECKED_CAST")
        val state = preferences as? MutableStateFlow<ReaderPreferences> ?: return
        state.value = state.value.copy(fontScale = value)
    }
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
