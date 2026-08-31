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
import app.openstory.chapters.sync.ChapterSyncService
import app.openstory.chapters.aggregation.ChapterAggregationEngine
import app.openstory.chapters.normalization.ChapterLabelParser
import app.openstory.chapters.source.ChapterSource
import app.openstory.chapters.source.ChapterSourceRegistry
import app.openstory.common.FakeClock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingRejection
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.library.mapping.ContentMappingWriteResult
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.catalog.ui.state.ContentState
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    fun chapterSnapshotRendersBeforeReaderCapability() = runTest(dispatcher.scheduler) {
        val capabilityGate = CompletableDeferred<Unit>()
        val viewModel = chapterViewModel(
            repository = FakeChapterRepository(listOf(group("1"))),
            readerSources = gatedReaderSources(capabilityGate),
        )
        observe(viewModel.state)
        runCurrent()

        val content = assertIs<ContentState.Ready<ChapterListContent>>(viewModel.state.value.content).value
        assertEquals(1, content.chapterCount)
        assertEquals(ChapterCapabilityState.UNKNOWN, content.chapters.single().releases.single().readerCapability)
        assertEquals(null, viewModel.state.value.observationIssue)
    }

    @Test
    fun firstEmptyChapterSnapshotIsReadyEmpty() = runTest(dispatcher.scheduler) {
        val viewModel = chapterViewModel(FakeChapterRepository(emptyList()))
        observe(viewModel.state)
        runCurrent()

        val content = assertIs<ContentState.Ready<ChapterListContent>>(viewModel.state.value.content).value
        assertEquals(0, content.chapterCount)
        assertTrue(content.chapters.isEmpty())
    }

    @Test
    fun firstChapterObservationFailureIsBlockingFailed() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(
            initial = emptyList(),
            observeFactory = { flow { error("db") } },
        )
        val viewModel = chapterViewModel(repository)
        observe(viewModel.state)
        runCurrent()

        val failed = assertIs<ContentState.Failed>(viewModel.state.value.content)
        assertEquals("chapter.list.observe_failed", failed.failure.code)
        assertTrue(failed.failure.retryable)
    }

    @Test
    fun readerCapabilityPendingIsNotAuthoritativeUnsupported() = runTest(dispatcher.scheduler) {
        val capabilityGate = CompletableDeferred<Unit>()
        val viewModel = chapterViewModel(
            repository = FakeChapterRepository(listOf(group("1"))),
            readerSources = gatedReaderSources(capabilityGate),
        )
        observe(viewModel.state)
        runCurrent()

        val release = viewModel.state.value.readyContent().chapters.single().releases.single()
        assertEquals(ChapterCapabilityState.UNKNOWN, release.readerCapability)
        assertEquals(ChapterCapabilityState.UNKNOWN, release.downloadCapability)
        assertFalse(viewModel.state.value.readyContent().readerAvailabilityResolved)
    }

    @Test
    fun readerCapabilityPendingRemainsUnresolvedWhenFilterHidesAllChapters() = runTest(dispatcher.scheduler) {
        val capabilityGate = CompletableDeferred<Unit>()
        val viewModel = chapterViewModel(
            repository = FakeChapterRepository(listOf(group("1"))),
            readerSources = gatedReaderSources(capabilityGate),
        )
        observe(viewModel.state)
        runCurrent()

        viewModel.selectFilter(ChapterListFilter.MULTI_RELEASE)
        runCurrent()

        val content = viewModel.state.value.readyContent()
        assertTrue(content.chapters.isEmpty())
        assertEquals(1, content.chapterCount)
        assertEquals(1, content.releaseTargets.size)
        assertFalse(content.readerAvailabilityResolved)
    }

    @Test
    fun partialReaderCapabilityLookupFailureDoesNotPublishHalfAuthoritativeSnapshot() = runTest(dispatcher.scheduler) {
        val readerSources = object : ReaderSourceAvailability {
            override suspend fun enabledPluginIds(): Set<PluginId> = setOf(PluginId("content.0"))
            override suspend fun offlineDownloadPluginIds(): Set<PluginId> = error("offline capability failed")
        }
        val viewModel = chapterViewModel(
            repository = FakeChapterRepository(listOf(group("1"))),
            readerSources = readerSources,
        )
        observe(viewModel.state)
        runCurrent()

        val release = viewModel.state.value.readyContent().chapters.single().releases.single()
        assertEquals(ChapterCapabilityState.UNKNOWN, release.readerCapability)
        assertEquals(ChapterCapabilityState.UNKNOWN, release.downloadCapability)
        assertFalse(viewModel.state.value.readyContent().readerAvailabilityResolved)
        assertEquals("chapter.list.reader_capability_failed", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun readerCapabilityFailureKeepsChapters() = runTest(dispatcher.scheduler) {
        val viewModel = chapterViewModel(
            repository = FakeChapterRepository(listOf(group("1"))),
            readerSources = failingReaderSources(),
        )
        observe(viewModel.state)
        runCurrent()

        val content = viewModel.state.value.readyContent()
        assertEquals(1, content.chapterCount)
        assertEquals(ChapterCapabilityState.UNKNOWN, content.chapters.single().releases.single().readerCapability)
        assertEquals("chapter.list.reader_capability_failed", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun manualRefreshKeepsReadyChaptersVisible() = runTest(dispatcher.scheduler) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = chapterViewModel(
            FakeChapterRepository(listOf(group("1"))),
            syncService = chapterSyncService(
                mappings = object : ContentMappingRepository by EmptyMappingRepository {
                    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> = flow {
                        gate.await()
                        emit(emptyList())
                    }
                },
            ),
        )
        observe(viewModel.state)
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refresh.inProgress)
        assertEquals(1, viewModel.state.value.readyContent().chapterCount)
        gate.complete(Unit)
        runCurrent()
    }

    @Test
    fun newRefreshAttemptClearsOnlyPriorRefreshFailure() = runTest(dispatcher.scheduler) {
        val secondAttemptGate = CompletableDeferred<Unit>()
        var sourceCalls = 0
        val sources = object : ChapterSourceRegistry {
            override suspend fun enabled(): List<ChapterSource> {
                sourceCalls += 1
                if (sourceCalls == 1) error("first failure")
                secondAttemptGate.await()
                return emptyList()
            }
        }
        val viewModel = chapterViewModel(
            FakeChapterRepository(listOf(group("1"))),
            syncService = chapterSyncService(sources = sources),
        )
        observe(viewModel.state)
        runCurrent()

        viewModel.refresh()
        runCurrent()
        assertEquals("chapter.sync_failed", viewModel.state.value.refresh.failure?.code)

        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.state.value.refresh.inProgress)
        assertEquals(null, viewModel.state.value.refresh.failure)
        secondAttemptGate.complete(Unit)
        runCurrent()
    }

    @Test
    fun correctionFailureDoesNotOverwriteRefreshOrObservationIssue() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(
            initial = listOf(group("1")),
            saveOverrideFailure = IllegalStateException("write failed"),
        )
        val viewModel = chapterViewModel(
            repository = repository,
            readerSources = failingReaderSources(),
            syncService = chapterSyncService(
                sources = object : ChapterSourceRegistry {
                    override suspend fun enabled(): List<ChapterSource> = error("sync failed")
                },
            ),
        )
        observe(viewModel.state)
        runCurrent()
        viewModel.refresh()
        runCurrent()
        viewModel.separate(ChapterReleaseId("release:1:0"))
        runCurrent()

        assertEquals("chapter.list.reader_capability_failed", viewModel.state.value.observationIssue?.code)
        assertEquals("chapter.sync_failed", viewModel.state.value.refresh.failure?.code)
        assertEquals("chapter.list.correction_failed", viewModel.state.value.correctionFailure?.code)
    }

    @Test
    fun retryObservationRestartsPostValueChapterFailureAndClearsOnlyThatIssue() = runTest(dispatcher.scheduler) {
        var attempts = 0
        val repository = FakeChapterRepository(
            initial = emptyList(),
            observeFactory = {
                attempts += 1
                if (attempts == 1) {
                    flow {
                        emit(listOf(group("1")))
                        error("db after value")
                    }
                } else {
                    flowOf(listOf(group("2")))
                }
            },
        )
        val viewModel = chapterViewModel(repository)
        observe(viewModel.state)
        runCurrent()

        assertEquals(CanonicalChapterId("chapter:1"), viewModel.state.value.readyContent().chapters.single().id)
        assertEquals("chapter.list.observe_failed", viewModel.state.value.observationIssue?.code)

        viewModel.retryObservation()
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(CanonicalChapterId("chapter:2"), viewModel.state.value.readyContent().chapters.single().id)
        assertEquals(null, viewModel.state.value.observationIssue)
    }

    @Test
    fun retryObservationRestartsReaderCapabilityWithoutRestartingChapters() = runTest(dispatcher.scheduler) {
        var capabilityAttempts = 0
        val readerSources = object : ReaderSourceAvailability {
            override suspend fun enabledPluginIds(): Set<PluginId> {
                capabilityAttempts += 1
                if (capabilityAttempts == 1) error("reader unavailable")
                return setOf(PluginId("content.0"))
            }

            override suspend fun offlineDownloadPluginIds(): Set<PluginId> = setOf(PluginId("content.0"))
        }
        val repository = FakeChapterRepository(listOf(group("1")))
        val viewModel = chapterViewModel(repository, readerSources = readerSources)
        observe(viewModel.state)
        runCurrent()
        assertEquals("chapter.list.reader_capability_failed", viewModel.state.value.observationIssue?.code)

        viewModel.retryObservation()
        runCurrent()

        assertEquals(2, capabilityAttempts)
        assertEquals(1, repository.observeCalls)
        assertEquals(null, viewModel.state.value.observationIssue)
        assertEquals(ChapterCapabilityState.SUPPORTED, viewModel.state.value.readyContent().chapters.single().releases.single().readerCapability)
    }

    @Test
    fun retryContentRestartsChapterObservationNotChapterSync() = runTest(dispatcher.scheduler) {
        var attempts = 0
        val repository = FakeChapterRepository(
            initial = emptyList(),
            observeFactory = {
                attempts += 1
                if (attempts == 1) flow { error("db") } else flowOf(listOf(group("1")))
            },
        )
        var syncCalls = 0
        val viewModel = chapterViewModel(
            repository = repository,
            syncService = chapterSyncService(
                sources = object : ChapterSourceRegistry {
                    override suspend fun enabled(): List<ChapterSource> {
                        syncCalls += 1
                        return emptyList()
                    }
                },
            ),
        )
        observe(viewModel.state)
        runCurrent()
        assertIs<ContentState.Failed>(viewModel.state.value.content)

        viewModel.retryContent()
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(0, syncCalls)
        assertEquals(1, viewModel.state.value.readyContent().chapterCount)
    }

    @Test
    fun projectsCanonicalChapterCountAndFilters() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1", releaseCount = 2), group("2")))
        val viewModel = chapterViewModel(repository)
        assertIs<ContentState.Pending>(viewModel.state.value.content)

        observe(viewModel.state)
        runCurrent()

        assertIs<ContentState.Ready<ChapterListContent>>(viewModel.state.value.content)
        assertEquals(2, viewModel.state.value.readyContent().chapterCount)

        viewModel.selectFilter(ChapterListFilter.MULTI_RELEASE)
        runCurrent()

        assertEquals(listOf(CanonicalChapterId("chapter:1")), viewModel.state.value.readyContent().chapters.map { it.id })
        assertEquals(2, viewModel.state.value.readyContent().chapters.single().releases.size)
        assertEquals(3, viewModel.state.value.readyContent().readableTargets.size)
    }

    @Test
    fun projectsCompactChapterIdentityTitleSourceNameAndNewestFirst() = runTest(dispatcher.scheduler) {
        val chapterTwelve = group(
            "12",
            releaseCount = 2,
            displayLabel = "Chapter 12 · The Locked Constellation",
        ).let { group ->
            group.copy(
                releases = group.releases.mapIndexed { index, release ->
                    if (index == 0) release.copy(pluginId = PluginId("org.mangadex.content")) else release
                },
            )
        }
        val repository = FakeChapterRepository(listOf(group("1"), chapterTwelve))
        val viewModel = chapterViewModel(repository)
        observe(viewModel.state)
        runCurrent()

        assertEquals(2, viewModel.state.value.readyContent().chapterCount)
        assertEquals(listOf("Chapter 12", "Chapter 1"), viewModel.state.value.readyContent().chapters.map { it.label })
        assertEquals("The Locked Constellation", viewModel.state.value.readyContent().chapters.first().title)
        assertEquals("MangaDex", viewModel.state.value.readyContent().chapters.first().releases.first().sourceName)
    }

    @Test
    fun keepsPartIdentityOutOfTitleAndPreservesNamedChapterDescription() = runTest(dispatcher.scheduler) {
        val partParsed = ParsedChapterLabel(
            kind = ChapterKind.NUMBERED,
            volume = null,
            chapter = BigDecimal("12"),
            part = 2,
            normalizedTitle = "the split",
        )
        val partGroup = group("12").let { group ->
            val displayLabel = "Chapter 12 · Part 2 · The Split"
            val releases = group.releases.map { release ->
                release.copy(displayLabel = displayLabel, parsedLabel = partParsed)
            }
            CanonicalChapterGroup(
                chapter = group.chapter.copy(
                    parsedLabel = partParsed,
                    displayLabel = displayLabel,
                    releaseIds = releases.mapTo(linkedSetOf()) { it.id },
                ),
                releases = releases,
            )
        }
        val prologueParsed = ParsedChapterLabel(
            kind = ChapterKind.PROLOGUE,
            volume = null,
            chapter = null,
            part = null,
            normalizedTitle = "to the war",
        )
        val prologueGroup = group("0").let { group ->
            val releases = group.releases.map { release ->
                release.copy(displayLabel = "Prologue to the War", parsedLabel = prologueParsed)
            }
            CanonicalChapterGroup(
                chapter = group.chapter.copy(
                    parsedLabel = prologueParsed,
                    displayLabel = "Prologue to the War",
                    releaseIds = releases.mapTo(linkedSetOf()) { it.id },
                ),
                releases = releases,
            )
        }
        val viewModel = chapterViewModel(FakeChapterRepository(listOf(prologueGroup, partGroup)))
        observe(viewModel.state)
        runCurrent()

        assertEquals("Chapter 12 · Part 2", viewModel.state.value.readyContent().chapters[0].label)
        assertEquals("The Split", viewModel.state.value.readyContent().chapters[0].title)
        assertEquals("Prologue", viewModel.state.value.readyContent().chapters[1].label)
        assertEquals("Prologue to the War", viewModel.state.value.readyContent().chapters[1].title)
    }

    @Test
    fun readableTargetsRemainStableAcrossPresentationFilters() = runTest(dispatcher.scheduler) {
        val viewModel = chapterViewModel(
            FakeChapterRepository(listOf(group("1", releaseCount = 2), group("2"))),
        )
        observe(viewModel.state)
        runCurrent()
        val targets = viewModel.state.value.readyContent().readableTargets

        viewModel.selectFilter(ChapterListFilter.MULTI_RELEASE)
        runCurrent()

        assertEquals(targets, viewModel.state.value.readyContent().readableTargets)
        assertEquals(3, viewModel.state.value.readyContent().readableTargets.size)
    }

    @Test
    fun newlySyncedReaderReleaseBecomesReadableWithoutRecreatingViewModel() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(emptyList())
        val viewModel = chapterViewModel(
            repository,
            readerPlugins = setOf(PluginId("content.0")),
        )
        observe(viewModel.state)
        runCurrent()
        assertEquals(emptyList(), viewModel.state.value.readyContent().readableTargets)

        repository.replace(listOf(group("1")))
        runCurrent()

        assertEquals(
            listOf(ChapterReleaseId("release:1:0")),
            viewModel.state.value.readyContent().readableTargets.map { it.releaseId },
        )
    }

    @Test
    fun listOnlyReleasesStayVisibleButNeverBecomeReaderTargets() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1", releaseCount = 2)))
        val viewModel = chapterViewModel(
            repository,
            readerPlugins = setOf(PluginId("content.1")),
        )
        observe(viewModel.state)
        runCurrent()

        val releases = viewModel.state.value.readyContent().chapters.single().releases
        assertEquals(listOf(ChapterCapabilityState.UNSUPPORTED, ChapterCapabilityState.SUPPORTED), releases.map { it.readerCapability })
        assertEquals(
            listOf(ChapterReleaseId("release:1:0"), ChapterReleaseId("release:1:1")),
            viewModel.state.value.readyContent().releaseTargets.map { it.releaseId },
        )
        assertEquals(listOf(ChapterReleaseId("release:1:1")), viewModel.state.value.readyContent().readableTargets.map { it.releaseId })
    }


    @Test
    fun onlineOnlyReaderReleaseIsReadableButExcludedFromDownloadTargets() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1", releaseCount = 2)))
        val viewModel = chapterViewModel(
            repository,
            readerPlugins = setOf(PluginId("content.0"), PluginId("content.1")),
            offlineDownloadPlugins = setOf(PluginId("content.1")),
        )
        observe(viewModel.state)
        runCurrent()

        val releases = viewModel.state.value.readyContent().chapters.single().releases
        assertEquals(listOf(ChapterCapabilityState.SUPPORTED, ChapterCapabilityState.SUPPORTED), releases.map { it.readerCapability })
        assertEquals(listOf(ChapterCapabilityState.UNSUPPORTED, ChapterCapabilityState.SUPPORTED), releases.map { it.downloadCapability })
        assertEquals(
            listOf(ChapterReleaseId("release:1:0"), ChapterReleaseId("release:1:1")),
            viewModel.state.value.readyContent().readableTargets.map { it.releaseId },
        )
        assertEquals(
            listOf(ChapterReleaseId("release:1:1")),
            viewModel.state.value.readyContent().downloadableTargets.map { it.releaseId },
        )
    }

    @Test
    fun tombstonesStayHiddenUntilExplicitlyRequested() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1"), group("2", tombstoned = true)))
        val viewModel = chapterViewModel(repository)
        observe(viewModel.state)
        runCurrent()

        assertEquals(listOf(CanonicalChapterId("chapter:1")), viewModel.state.value.readyContent().chapters.map { it.id })

        viewModel.setTombstonesVisible(true)
        runCurrent()

        assertEquals(2, viewModel.state.value.readyContent().chapters.size)
    }

    @Test
    fun correctionCommandsPersistProtectedOverrides() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1")))
        val viewModel = chapterViewModel(repository)
        observe(viewModel.state)
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

    @Test
    fun refreshRunsChapterSyncAndExposesRefreshingState() = runTest(dispatcher.scheduler) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val viewModel = chapterViewModel(
            FakeChapterRepository(listOf(group("1"))),
            syncService = chapterSyncService(
                mappings = object : ContentMappingRepository by EmptyMappingRepository {
                    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> = flow {
                        gate.await()
                        emit(emptyList())
                    }
                },
            ),
        )
        observe(viewModel.state)
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refresh.inProgress)

        gate.complete(Unit)
        runCurrent()

        assertFalse(viewModel.state.value.refresh.inProgress)
        assertEquals(null, viewModel.state.value.refresh.failure?.code)
    }

    @Test
    fun refreshFailureKeepsChaptersAndExposesNonBlockingFailure() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1")))
        val viewModel = chapterViewModel(
            repository,
            syncService = chapterSyncService(
                sources = object : ChapterSourceRegistry {
                    override suspend fun enabled(): List<ChapterSource> = error("registry unavailable")
                },
            ),
        )
        observe(viewModel.state)
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertFalse(viewModel.state.value.refresh.inProgress)
        assertEquals(listOf(CanonicalChapterId("chapter:1")), viewModel.state.value.readyContent().chapters.map { it.id })
        assertEquals("chapter.sync_failed", viewModel.state.value.refresh.failure?.code)
    }

    private fun TestScope.observe(state: StateFlow<ChapterListUiState>) {
        backgroundScope.launch(dispatcher) { state.collect {} }
    }
}

private fun chapterViewModel(
    repository: ChapterRepository,
    readerPlugins: Set<PluginId> = setOf(PluginId("content.0"), PluginId("content.1")),
    offlineDownloadPlugins: Set<PluginId> = readerPlugins,
    readerSources: ReaderSourceAvailability? = null,
    syncService: ChapterSyncService = chapterSyncService(),
) = ChapterListViewModel(
    ChapterListAssistedArgs(STORY_ID),
    repository,
    readerSources ?: object : ReaderSourceAvailability {
        override suspend fun enabledPluginIds(): Set<PluginId> = readerPlugins
        override suspend fun offlineDownloadPluginIds(): Set<PluginId> = offlineDownloadPlugins
    },
    syncService,
)

private fun chapterSyncService(
    mappings: ContentMappingRepository = EmptyMappingRepository,
    sources: ChapterSourceRegistry = object : ChapterSourceRegistry {
        override suspend fun enabled(): List<ChapterSource> = emptyList()
    },
    repository: ChapterRepository = FakeChapterRepository(emptyList()),
) = ChapterSyncService(
    mappings = mappings,
    sources = sources,
    chapters = repository,
    aggregation = ChapterAggregationEngine(),
    parser = ChapterLabelParser(),
    clock = FakeClock(1_000L),
)

private object EmptyMappingRepository : ContentMappingRepository {
    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> = flowOf(emptyList())
    override fun observeAll(): Flow<List<ContentMapping>> = flowOf(emptyList())
    override suspend fun compareAndWrite(
        mapping: ContentMapping,
        replaceableOrigins: Set<ContentMappingOrigin>,
    ): ContentMappingWriteResult = error("not used")
    override suspend fun reject(rejection: ContentMappingRejection) = Unit
    override suspend fun isRejected(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
        policyVersion: Int,
    ): Boolean = false
}

private class FakeChapterRepository(
    initial: List<CanonicalChapterGroup>,
    private val observeFactory: (() -> Flow<List<CanonicalChapterGroup>>)? = null,
    private val saveOverrideFailure: Exception? = null,
) : ChapterRepository {
    private val groups = MutableStateFlow(initial)
    val savedOverrides = mutableListOf<ChapterAggregationOverride>()
    var observeCalls: Int = 0
        private set

    fun replace(value: List<CanonicalChapterGroup>) {
        groups.value = value
    }

    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = groups
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> {
        observeCalls += 1
        return observeFactory?.invoke() ?: groups
    }
    override suspend fun snapshot(storyId: StoryId) = ChapterGraphSnapshot(emptyList(), emptyList(), emptyList())
    override suspend fun commit(mutation: ChapterMutation): ChapterCommitResult = ChapterCommitResult.Success
    override suspend fun saveOverride(storyId: StoryId, override: ChapterAggregationOverride) {
        saveOverrideFailure?.let { throw it }
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
    displayLabel: String = "Chapter $number",
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
        CanonicalChapter(chapterId, STORY_ID, label, displayLabel, tombstoned, releases.mapTo(linkedSetOf()) { it.id }),
        releases,
    )
}

private fun ChapterListUiState.readyContent(): ChapterListContent =
    (content as ContentState.Ready<ChapterListContent>).value

private fun gatedReaderSources(gate: CompletableDeferred<Unit>) = object : ReaderSourceAvailability {
    override suspend fun enabledPluginIds(): Set<PluginId> {
        gate.await()
        return setOf(PluginId("content.0"))
    }

    override suspend fun offlineDownloadPluginIds(): Set<PluginId> = setOf(PluginId("content.0"))
}

private fun failingReaderSources() = object : ReaderSourceAvailability {
    override suspend fun enabledPluginIds(): Set<PluginId> = error("reader capability failed")
    override suspend fun offlineDownloadPluginIds(): Set<PluginId> = error("reader capability failed")
}

private val STORY_ID = StoryId("story:chapters-ui")
