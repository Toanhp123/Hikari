package app.openstory.reader.ui

import androidx.lifecycle.SavedStateHandle
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.common.FakeClock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.assets.ContentFetchArbiter
import app.openstory.reader.assets.ReaderAssetCoordinator
import app.openstory.reader.assets.ReaderAssetManifestFactory
import app.openstory.reader.assets.ReaderAssetSessionPort
import app.openstory.reader.assets.ReaderDeliveryManifestReplacement
import app.openstory.reader.assets.ReaderViewportDirection
import app.openstory.reader.assets.ReaderViewportSnapshot
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.preferences.ReaderPreferencesPort
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.routing.ContentSourceExecutionLane
import app.openstory.reader.routing.ReaderHalfOpenProbeRegistry
import app.openstory.reader.routing.ReaderRouteCoordinator
import app.openstory.reader.routing.ReaderRouteSessionFactory
import app.openstory.reader.routing.ReaderSourceHealthRegistry
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelContinuityTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initialForegroundWaitsForFirstPreferencesAndGraphAndObservesGraphOnce() = runTest(dispatcher.scheduler) {
        val chapters = TestChapterRepository()
        val preferences = MutableSharedFlow<ReaderPreferences>()
        val source = ControlledReaderSource().apply {
            enqueueSuccess("release-a", document("release-a"))
        }
        val progress = TestProgressRepository()
        ReaderViewModel(
            ReaderAssistedArgs("story", "chapter-1", null),
            SavedStateHandle(),
            chapters,
            routeFactory(source, progress),
            testReaderAssetCoordinator(),
            progress,
            FakeClock(100),
            TestPreferencesPort(preferences),
        )
        runCurrent()

        assertEquals(1, chapters.observeCalls)
        assertEquals(0, chapters.snapshotCalls)
        assertTrue(source.fetches.isEmpty())

        preferences.emit(ReaderPreferences(languageOrder = listOf("ja", "en")))
        runCurrent()
        assertTrue(source.fetches.isEmpty())

        chapters.emit(groups(chapter("chapter-1", "release-a")))
        runCurrent()
        assertEquals(listOf("release-a"), source.fetches)
        assertEquals(1, chapters.observeCalls)
        assertEquals(0, chapters.snapshotCalls)
    }

    @Test
    fun reactiveGraphRefreshesCommittedPreviousAndNextNavigationWithoutReloadingDocument() =
        runTest(dispatcher.scheduler) {
        val a = chapter("chapter-1", "release-a")
        val b = chapter("chapter-2", "release-b")
        val c = chapter("chapter-3", "release-c")
        val chapters = TestChapterRepository(groups(a, b))
        val source = ControlledReaderSource().apply {
            enqueueSuccess("release-a", document("release-a"))
        }
        val progress = TestProgressRepository()
        val viewModel = viewModel(chapters, source, progress, SavedStateHandle())
        runCurrent()
        val committedDocument = viewModel.state.value.document
        assertEquals("chapter-2", viewModel.state.value.nextChapterId?.value)

        chapters.emit(groups(a, c, b))
        runCurrent()

        assertSame(committedDocument, viewModel.state.value.document)
        assertNull(viewModel.state.value.previousChapterId)
        assertEquals("chapter-3", viewModel.state.value.nextChapterId?.value)
        assertEquals(listOf("release-a"), source.fetches)
    }

    @Test
    fun reactiveGraphExcludesTombstonedChapterFromNavigation() = runTest(dispatcher.scheduler) {
        val active = chapter("chapter-1", "release-a")
        val tombstoned = chapter("chapter-2", "release-b").let { (chapter, release) ->
            chapter.copy(tombstoned = true) to release
        }
        val chapters = TestChapterRepository(groups(active, tombstoned))
        val source = ControlledReaderSource().apply {
            enqueueSuccess("release-a", document("release-a"))
        }
        val progress = TestProgressRepository()
        val viewModel = viewModel(chapters, source, progress, SavedStateHandle())
        runCurrent()

        assertEquals("chapter-1", viewModel.state.value.committedChapterId?.value)
        assertNull(viewModel.state.value.nextChapterId)
        assertEquals(listOf("release-a"), source.fetches)
    }

    @Test
    fun openingNextChapterKeepsCommittedDocumentSavedIdentityAndProgressOwnerUntilCommit() =
        runTest(dispatcher.scheduler) {
        val graph = groups(
            chapter("chapter-1", "release-a"),
            chapter("chapter-2", "release-b"),
        )
        val chapters = TestChapterRepository(graph)
        val source = ControlledReaderSource().apply {
            enqueueSuccess("release-a", document("release-a"))
        }
        val pendingNext = source.enqueuePending("release-b")
        val progress = TestProgressRepository()
        val savedState = SavedStateHandle()
        val viewModel = viewModel(chapters, source, progress, savedState)
        runCurrent()
        val committedDocument = viewModel.state.value.document

        viewModel.openChapter(CanonicalChapterId("chapter-2"))
        runCurrent()

        assertSame(committedDocument, viewModel.state.value.document)
        assertFalse(viewModel.state.value.loading)
        assertEquals("chapter-2", viewModel.state.value.transitionTargetChapterId?.value)
        assertEquals("chapter-1", savedState.get<String>("reader.chapter-id"))
        assertEquals("release-a", savedState.get<String>("reader.release-id"))

        viewModel.updatePosition(ReadingPosition("block", 7, 0.7f), completed = false)
        viewModel.flushProgress()
        runCurrent()
        assertEquals("chapter-1", progress.lastSaved?.canonicalChapterId?.value)
        assertEquals("release-a", progress.lastSaved?.releaseId?.value)

        pendingNext.complete(ReaderSourceResult.Success(document("release-b")))
        runCurrent()

        assertEquals("chapter-2", viewModel.state.value.committedChapterId?.value)
        assertEquals("release-b", viewModel.state.value.selectedReleaseId?.value)
        assertNull(viewModel.state.value.transitionTargetChapterId)
        assertEquals("chapter-2", savedState.get<String>("reader.chapter-id"))
        assertEquals("release-b", savedState.get<String>("reader.release-id"))
    }

    @Test
    fun failedTransitionKeepsCommittedContentAndRetryTargetsTheFailedTransition() = runTest(dispatcher.scheduler) {
        val chapters = TestChapterRepository(
            groups(chapter("chapter-1", "release-a"), chapter("chapter-2", "release-b")),
        )
        val source = ControlledReaderSource().apply {
            enqueueSuccess("release-a", document("release-a"))
            enqueueFailure("release-b", "reader.source_failed", retryable = true)
            enqueueSuccess("release-b", document("release-b"))
        }
        val progress = TestProgressRepository()
        val savedState = SavedStateHandle()
        val viewModel = viewModel(chapters, source, progress, savedState)
        runCurrent()
        val committed = viewModel.state.value.document

        viewModel.openChapter(CanonicalChapterId("chapter-2"))
        runCurrent()

        assertSame(committed, viewModel.state.value.document)
        assertEquals("chapter-2", viewModel.state.value.transitionTargetChapterId?.value)
        assertEquals("reader.source_failed", viewModel.state.value.failure)
        assertEquals("chapter-1", savedState.get<String>("reader.chapter-id"))

        viewModel.retry()
        runCurrent()

        assertEquals(listOf("release-a", "release-b", "release-b"), source.fetches)
        assertEquals("chapter-2", viewModel.state.value.committedChapterId?.value)
        assertEquals("chapter-2", savedState.get<String>("reader.chapter-id"))
    }

    @Test
    fun reopeningFailedTransitionRetriesSameTargetWithoutDroppingCommittedContent() = runTest(dispatcher.scheduler) {
        val chapters = TestChapterRepository(
            groups(chapter("chapter-1", "release-a"), chapter("chapter-2", "release-b")),
        )
        val source = ControlledReaderSource().apply {
            enqueueSuccess("release-a", document("release-a"))
            enqueueFailure("release-b", "reader.source_failed", retryable = true)
            enqueueSuccess("release-b", document("release-b"))
        }
        val progress = TestProgressRepository()
        val savedState = SavedStateHandle()
        val viewModel = viewModel(chapters, source, progress, savedState)
        runCurrent()
        val committed = viewModel.state.value.document

        viewModel.openChapter(CanonicalChapterId("chapter-2"))
        runCurrent()
        assertEquals("reader.source_failed", viewModel.state.value.failure)
        assertSame(committed, viewModel.state.value.document)

        viewModel.openChapter(CanonicalChapterId("chapter-2"))
        runCurrent()

        assertEquals(listOf("release-a", "release-b", "release-b"), source.fetches)
        assertEquals("chapter-2", viewModel.state.value.committedChapterId?.value)
        assertEquals("chapter-2", savedState.get<String>("reader.chapter-id"))
    }

    @Test
    fun selectingReleaseDoesNotPersistOrReplaceCommittedContentUntilTheSelectionCommits() =
        runTest(dispatcher.scheduler) {
        val first = chapter("chapter-1", "release-a")
        val alternate = first.second.copy(
            id = ChapterReleaseId("release-b"),
            sourceReleaseId = "release-b",
        )
        val chapters = TestChapterRepository(
            listOf(CanonicalChapterGroup(first.first, listOf(first.second, alternate))),
        )
        val source = ControlledReaderSource().apply {
            enqueueSuccess("release-a", document("release-a"))
        }
        val pending = source.enqueuePending("release-b")
        val progress = TestProgressRepository()
        val savedState = SavedStateHandle()
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter-1", "release-a"),
            savedState,
            chapters,
            routeFactory(source, progress),
            testReaderAssetCoordinator(),
            progress,
            FakeClock(100),
            TestPreferencesPort(MutableStateFlow(ReaderPreferences())),
        )
        runCurrent()
        val committed = viewModel.state.value.document

        viewModel.selectRelease(ChapterReleaseId("release-b"))
        runCurrent()

        assertSame(committed, viewModel.state.value.document)
        assertEquals("release-a", viewModel.state.value.selectedReleaseId?.value)
        assertEquals("release-a", savedState.get<String>("reader.release-id"))
        assertEquals("release-b", viewModel.state.value.transitionTargetReleaseId?.value)

        pending.complete(ReaderSourceResult.Success(document("release-b")))
        runCurrent()
        assertEquals("release-b", viewModel.state.value.selectedReleaseId?.value)
        assertEquals("release-b", savedState.get<String>("reader.release-id"))
    }

    @Test
    fun rapidNavigationCannotLetStaleIntermediateTargetCommit() = runTest(dispatcher.scheduler) {
        val chapters = TestChapterRepository(
            groups(
                chapter("chapter-1", "release-a"),
                chapter("chapter-2", "release-b"),
                chapter("chapter-3", "release-c"),
            ),
        )
        val source = ControlledReaderSource().apply {
            enqueueSuccess("release-a", document("release-a"))
        }
        val stale = source.enqueuePending("release-b")
        source.enqueueSuccess("release-c", document("release-c"))
        val progress = TestProgressRepository()
        val savedState = SavedStateHandle()
        val viewModel = viewModel(chapters, source, progress, savedState)
        runCurrent()

        viewModel.openChapter(CanonicalChapterId("chapter-2"))
        runCurrent()
        viewModel.openChapter(CanonicalChapterId("chapter-3"))
        runCurrent()

        assertEquals("chapter-3", viewModel.state.value.committedChapterId?.value)
        assertEquals("release-c", viewModel.state.value.selectedReleaseId?.value)
        assertEquals("chapter-3", savedState.get<String>("reader.chapter-id"))

        stale.complete(ReaderSourceResult.Success(document("release-b")))
        runCurrent()
        assertEquals("chapter-3", viewModel.state.value.committedChapterId?.value)
        assertEquals("release-c", viewModel.state.value.selectedReleaseId?.value)
        assertEquals("chapter-3", savedState.get<String>("reader.chapter-id"))
    }

    @Test
    fun exactRestorationRequiresCommittedReleaseAndFingerprintMatch() = runTest(dispatcher.scheduler) {
        val chapters = TestChapterRepository(groups(chapter("chapter-1", "release-a")))
        val source = ControlledReaderSource().apply {
            enqueueSuccess("release-a", document("release-a", fingerprint = "fp-new"))
        }
        val progress = TestProgressRepository(
            progress("chapter-1", "release-a", "fp-old", ReadingPosition("saved", 12, 0.6f)),
        )
        val viewModel = viewModel(chapters, source, progress, SavedStateHandle())
        runCurrent()

        assertNull(viewModel.state.value.restoredBlockId)
        assertEquals(0, viewModel.state.value.restoredCharacterOffset)
        assertEquals(0f, viewModel.state.value.restoredProgressFraction)
    }

    @Test
    fun boundedAttemptExhaustionProducesOneVisibleFailureTransitionForGeneration() = runTest(dispatcher.scheduler) {
        val base = chapter("chapter-1", "release-a")
        val releases = listOf(
            base.second.copy(pluginId = PluginId("plugin-a")),
            base.second.copy(
                id = ChapterReleaseId("release-b"),
                pluginId = PluginId("plugin-b"),
                sourceReleaseId = "release-b",
            ),
            base.second.copy(
                id = ChapterReleaseId("release-c"),
                pluginId = PluginId("plugin-c"),
                sourceReleaseId = "release-c",
            ),
            base.second.copy(
                id = ChapterReleaseId("release-d"),
                pluginId = PluginId("plugin-d"),
                sourceReleaseId = "release-d",
            ),
        )
        val group = CanonicalChapterGroup(
            chapter = base.first.copy(releaseIds = releases.mapTo(linkedSetOf()) { it.id }),
            releases = releases,
        )
        val chapters = TestChapterRepository(listOf(group))
        val sources = releases.map { release ->
            ControlledReaderSource(release.pluginId).apply {
                enqueueFailure(release.id.value, "reader.source_failed", retryable = true)
            }
        }
        val progress = TestProgressRepository()
        val viewModel = ReaderViewModel(
            ReaderAssistedArgs("story", "chapter-1", null),
            SavedStateHandle(),
            chapters,
            routeFactory(sources, progress),
            testReaderAssetCoordinator(),
            progress,
            FakeClock(100),
            TestPreferencesPort(MutableStateFlow(ReaderPreferences())),
        )
        val visibleFailures = mutableListOf<String>()
        var previousFailure: String? = null
        val observer = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.state.collect { state ->
                val failure = state.failure
                if (failure != previousFailure && failure != null) visibleFailures += failure
                previousFailure = failure
            }
        }

        runCurrent()

        assertEquals(4, sources.sumOf { it.fetches.size })
        assertEquals(4, sources.flatMap { it.fetches }.distinct().size)
        assertEquals(listOf("reader.source_failed"), visibleFailures)
        assertEquals("reader.source_failed", viewModel.state.value.failure)
        assertNull(viewModel.state.value.document)
        observer.cancel()
    }

    @Test
    fun newerCommittedAssetSnapshotUpdatesRequestsWithoutReloadingSemanticDocument() =
        runTest(dispatcher.scheduler) {
            val chapter = chapter("chapter-1", "release-a")
            val chapters = TestChapterRepository(groups(chapter))
            val source = ControlledReaderSource().apply {
                enqueueSuccess(
                    "release-a",
                    imageDocument("release-a", "https://example.test/page-a.jpg"),
                )
            }
            val progress = TestProgressRepository()
            val assetCoordinator = testReaderAssetCoordinator()
            val viewModel = ReaderViewModel(
                ReaderAssistedArgs("story", "chapter-1", null),
                SavedStateHandle(),
                chapters,
                routeFactory(source, progress, assetCoordinator),
                assetCoordinator,
                progress,
                FakeClock(100),
                TestPreferencesPort(MutableStateFlow(ReaderPreferences())),
            )
            runCurrent()

            val committedDocument = assertNotNull(viewModel.state.value.document)
            val initialAssets = assertNotNull(viewModel.state.value.assets)
            val initialRequest = assertNotNull(initialAssets.requestForBlockId("page"))
            assertEquals(1L, initialAssets.manifestRevision)

            val refreshedManifest = assertNotNull(
                ReaderAssetManifestFactory().create(
                    sessionId = initialAssets.manifest.sessionId,
                    storyId = StoryId("story"),
                    canonicalChapterId = CanonicalChapterId("chapter-1"),
                    selectedRelease = chapter.second,
                    graphRevision = initialAssets.manifest.graphRevision,
                    document = imageDocument("release-a", "https://example.test/page-b.jpg"),
                    imageSourcePolicy = source.imageSourcePolicy,
                    sourcePluginId = source.pluginId,
                ),
            )

            assertIs<ReaderDeliveryManifestReplacement.Applied>(
                assetCoordinator.replaceDeliveryManifest(
                    sessionId = initialAssets.manifest.sessionId,
                    expectedManifestRevision = initialAssets.manifestRevision,
                    refreshedManifest = refreshedManifest,
                ),
            )
            runCurrent()

            val refreshedAssets = assertNotNull(viewModel.state.value.assets)
            assertEquals(2L, refreshedAssets.manifestRevision)
            assertNotEquals(
                initialRequest.descriptor.key,
                assertNotNull(refreshedAssets.requestForBlockId("page")).descriptor.key,
            )
            assertSame(committedDocument, viewModel.state.value.document)
            assertEquals(listOf("release-a"), source.fetches)
        }

    @Test
    fun duplicateCurrentViewportRemainsAcceptedByPresentationBoundary() =
        runTest(dispatcher.scheduler) {
            val chapters = TestChapterRepository(groups(chapter("chapter-1", "release-a")))
            val source = ControlledReaderSource().apply {
                enqueueSuccess(
                    "release-a",
                    imageDocument("release-a", "https://example.test/page-a.jpg"),
                )
            }
            val progress = TestProgressRepository()
            val assetCoordinator = testReaderAssetCoordinator()
            val viewModel = ReaderViewModel(
                ReaderAssistedArgs("story", "chapter-1", null),
                SavedStateHandle(),
                chapters,
                routeFactory(source, progress, assetCoordinator),
                assetCoordinator,
                progress,
                FakeClock(100),
                TestPreferencesPort(MutableStateFlow(ReaderPreferences())),
            )
            runCurrent()
            val assets = assertNotNull(viewModel.state.value.assets)
            val request = assertNotNull(assets.requestForBlockId("page"))
            val snapshot = ReaderViewportSnapshot(
                sessionId = request.sessionId,
                manifestRevision = request.manifestRevision,
                leadingVisibleImageOrdinal = request.descriptor.imageOrdinal,
                trailingVisibleImageOrdinal = request.descriptor.imageOrdinal,
                direction = ReaderViewportDirection.IDLE,
                chapterProgressBasisPoints = 0,
            )

            assertTrue(viewModel.updateAssetViewport(snapshot))
            assertTrue(viewModel.updateAssetViewport(snapshot))
        }

    @Test
    fun routeInvalidationFromRetainedContentDoesNotCancelPendingNavigation() =
        runTest(dispatcher.scheduler) {
            val chapters = TestChapterRepository(
                groups(
                    chapter("chapter-1", "release-a"),
                    chapter("chapter-2", "release-b"),
                ),
            )
            val source = ControlledReaderSource().apply {
                enqueueSuccess(
                    "release-a",
                    imageDocument("release-a", "https://example.test/page-a.jpg"),
                )
            }
            source.enqueuePending("release-b")
            val progress = TestProgressRepository()
            val assetCoordinator = testReaderAssetCoordinator()
            val viewModel = ReaderViewModel(
                ReaderAssistedArgs("story", "chapter-1", null),
                SavedStateHandle(),
                chapters,
                routeFactory(source, progress, assetCoordinator),
                assetCoordinator,
                progress,
                FakeClock(100),
                TestPreferencesPort(MutableStateFlow(ReaderPreferences())),
            )
            runCurrent()
            val revision = assertNotNull(viewModel.state.value.assets).manifestRevision

            viewModel.openChapter(CanonicalChapterId("chapter-2"))
            runCurrent()
            assertEquals("chapter-2", viewModel.state.value.transitionTargetChapterId?.value)

            viewModel.reloadRouteForInvalidatedAsset(revision)
            runCurrent()

            assertEquals(listOf("release-a", "release-b"), source.fetches)
            assertEquals("chapter-2", viewModel.state.value.transitionTargetChapterId?.value)
            assertEquals("chapter-1", viewModel.state.value.committedChapterId?.value)
        }

    @Test
    fun routeInvalidationReloadIsDedupedForTheCurrentManifestRevision() =
        runTest(dispatcher.scheduler) {
            val chapter = chapter("chapter-1", "release-a")
            val chapters = TestChapterRepository(groups(chapter))
            val source = ControlledReaderSource().apply {
                enqueueSuccess(
                    "release-a",
                    imageDocument("release-a", "https://example.test/page-a.jpg"),
                )
            }
            val pendingReload = source.enqueuePending("release-a")
            val progress = TestProgressRepository()
            val assetCoordinator = testReaderAssetCoordinator()
            val viewModel = ReaderViewModel(
                ReaderAssistedArgs("story", "chapter-1", null),
                SavedStateHandle(),
                chapters,
                routeFactory(source, progress, assetCoordinator),
                assetCoordinator,
                progress,
                FakeClock(100),
                TestPreferencesPort(MutableStateFlow(ReaderPreferences())),
            )
            runCurrent()
            val revision = assertNotNull(viewModel.state.value.assets).manifestRevision

            viewModel.reloadRouteForInvalidatedAsset(revision)
            viewModel.reloadRouteForInvalidatedAsset(revision)
            runCurrent()

            assertEquals(listOf("release-a", "release-a"), source.fetches)
            pendingReload.complete(
                ReaderSourceResult.Success(
                    imageDocument("release-a", "https://example.test/page-a.jpg"),
                ),
            )
            runCurrent()
            assertEquals(2, source.fetches.size)
        }

    @Test
    fun initialExhaustionWithoutCommittedContentBecomesUnavailable() = runTest(dispatcher.scheduler) {
        val chapters = TestChapterRepository(groups(chapter("chapter-1", "release-a")))
        val source = ControlledReaderSource().apply {
            enqueueFailure("release-a", "reader.source_failed", retryable = true)
        }
        val progress = TestProgressRepository()
        val viewModel = viewModel(chapters, source, progress, SavedStateHandle())
        runCurrent()

        assertNull(viewModel.state.value.document)
        assertFalse(viewModel.state.value.loading)
        assertEquals("reader.source_failed", viewModel.state.value.failure)
        assertTrue(viewModel.state.value.failureRetryable)
    }

    private fun viewModel(
        chapters: TestChapterRepository,
        source: ControlledReaderSource,
        progress: TestProgressRepository,
        savedState: SavedStateHandle,
    ): ReaderViewModel {
        val assetCoordinator = testReaderAssetCoordinator()
        return ReaderViewModel(
            ReaderAssistedArgs("story", "chapter-1", null),
            savedState,
            chapters,
            routeFactory(source, progress, assetCoordinator),
            assetCoordinator,
            progress,
            FakeClock(100),
            TestPreferencesPort(MutableStateFlow(ReaderPreferences())),
        )
    }

    private fun routeFactory(
        source: ControlledReaderSource,
        progress: ReadingProgressRepository,
        assetSessionPort: ReaderAssetSessionPort = ReaderAssetSessionPort.NO_OP,
    ) = routeFactory(listOf(source), progress, assetSessionPort)

    private fun routeFactory(
        sources: List<ControlledReaderSource>,
        progress: ReadingProgressRepository,
        assetSessionPort: ReaderAssetSessionPort = ReaderAssetSessionPort.NO_OP,
    ) = ReaderRouteSessionFactory(
        ReaderRouteCoordinator(
            store = ContinuityNoOpReaderDocumentStore,
            sources = object : ReaderDocumentSourceRegistry {
                override suspend fun enabled(): List<ReaderDocumentSource> = sources
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

private class TestChapterRepository(
    initial: List<CanonicalChapterGroup>? = null,
) : ChapterRepository {
    private val values = MutableSharedFlow<List<CanonicalChapterGroup>>(replay = 1)
    var observeCalls = 0
    var snapshotCalls = 0

    init {
        if (initial != null) values.tryEmit(initial)
    }

    suspend fun emit(groups: List<CanonicalChapterGroup>) {
        values.emit(groups)
    }

    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = values

    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> {
        observeCalls++
        return values
    }

    override suspend fun snapshot(storyId: StoryId): ChapterGraphSnapshot {
        snapshotCalls++
        error("ReaderViewModel M5 must not snapshot chapters per navigation")
    }

    override suspend fun commit(mutation: ChapterMutation): ChapterCommitResult = ChapterCommitResult.Success
    override suspend fun saveOverride(storyId: StoryId, override: ChapterAggregationOverride) = Unit
    override suspend fun syncState(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
    ): ChapterSyncState? = null
}

private class ControlledReaderSource(
    override val pluginId: PluginId = PluginId("plugin"),
    override val imageSourcePolicy: ReaderImageSourcePolicy = ReaderImageSourcePolicy.FAIL_CLOSED,
) : ReaderDocumentSource {
    private val responses = mutableMapOf<String, ArrayDeque<CompletableDeferred<ReaderSourceResult>>>()
    val fetches = mutableListOf<String>()

    fun enqueueSuccess(releaseId: String, document: ReaderDocument) {
        enqueue(releaseId, CompletableDeferred(ReaderSourceResult.Success(document)))
    }

    fun enqueueFailure(releaseId: String, code: String, retryable: Boolean) {
        enqueue(releaseId, CompletableDeferred(ReaderSourceResult.Failure(code, retryable)))
    }

    fun enqueuePending(releaseId: String): CompletableDeferred<ReaderSourceResult> =
        CompletableDeferred<ReaderSourceResult>().also { enqueue(releaseId, it) }

    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult {
        fetches += release.id.value
        val queue = responses[release.id.value]
        return checkNotNull(queue?.pollFirst()) {
            "No queued Reader response for ${release.id.value}"
        }.await()
    }

    private fun enqueue(releaseId: String, response: CompletableDeferred<ReaderSourceResult>) {
        responses.getOrPut(releaseId, ::ArrayDeque).addLast(response)
    }
}

private class TestProgressRepository(
    initial: ReadingProgress? = null,
) : ReadingProgressRepository {
    private val byChapter = mutableMapOf<CanonicalChapterId, ReadingProgress>()
    private val all = MutableStateFlow<List<ReadingProgress>>(emptyList())
    var lastSaved: ReadingProgress? = null
        private set

    init {
        if (initial != null) {
            byChapter[initial.canonicalChapterId] = initial
            all.value = listOf(initial)
        }
    }

    override fun observeAll(): Flow<List<ReadingProgress>> = all

    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> =
        MutableStateFlow(byChapter[chapterId])

    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = byChapter[chapterId]

    override suspend fun save(progress: ReadingProgress) {
        byChapter[progress.canonicalChapterId] = progress
        lastSaved = progress
        all.value = byChapter.values.toList()
    }
}

private class TestPreferencesPort(
    override val preferences: Flow<ReaderPreferences>,
) : ReaderPreferencesPort {
    override suspend fun setFontScale(value: Float) = Unit
}

private object ContinuityNoOpReaderDocumentStore : ReaderDocumentStore {
    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? = null
    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? = null
    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) = Unit
    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) = Unit
}

private fun groups(vararg chapters: Pair<CanonicalChapter, ChapterRelease>): List<CanonicalChapterGroup> =
    chapters.map { (chapter, release) -> CanonicalChapterGroup(chapter, listOf(release)) }

private fun chapter(chapterId: String, releaseId: String): Pair<CanonicalChapter, ChapterRelease> {
    val id = CanonicalChapterId(chapterId)
    val label = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null)
    val release = ChapterRelease(
        ChapterReleaseId(releaseId), StoryId("story"), PluginId("plugin"), "source-story", releaseId,
        chapterId, label, "en", 1, id,
    )
    return CanonicalChapter(id, StoryId("story"), label, chapterId, false, setOf(release.id)) to release
}

private fun document(releaseId: String, fingerprint: String = "fingerprint-${releaseId.removePrefix("release-")}") =
    ReaderDocument(
        title = releaseId,
        blocks = listOf(ReaderBlock.Paragraph("block", "Text")),
        fingerprint = fingerprint,
    )

private fun imageDocument(releaseId: String, imageUrl: String) = ReaderDocument(
    title = releaseId,
    blocks = listOf(ReaderBlock.ImagePage("page", "asset-page", imageUrl)),
    fingerprint = "fingerprint-${releaseId.removePrefix("release-")}",
)

private fun progress(
    chapterId: String,
    releaseId: String,
    fingerprint: String,
    position: ReadingPosition,
) = ReadingProgress(
    StoryId("story"),
    CanonicalChapterId(chapterId),
    ChapterReleaseId(releaseId),
    fingerprint,
    position,
    null,
    10,
)
