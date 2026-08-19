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
    fun projectsCanonicalUnreadCountExpansionAndFilters() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1", releaseCount = 2), group("2")))
        val viewModel = chapterViewModel(repository)
        assertTrue(viewModel.state.value.loading)

        observe(viewModel.state)
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals(2, viewModel.state.value.unreadCount)
        assertFalse(viewModel.state.value.chapters.first().expanded)

        viewModel.toggleExpanded(CanonicalChapterId("chapter:1"))
        viewModel.selectFilter(ChapterListFilter.MULTI_RELEASE)
        runCurrent()

        assertEquals(listOf(CanonicalChapterId("chapter:1")), viewModel.state.value.chapters.map { it.id })
        assertEquals(2, viewModel.state.value.chapters.single().releases.size)
        assertTrue(viewModel.state.value.chapters.single().expanded)
        assertEquals(3, viewModel.state.value.readableTargets.size)
    }

    @Test
    fun readableTargetsRemainStableAcrossPresentationFilters() = runTest(dispatcher.scheduler) {
        val viewModel = chapterViewModel(
            FakeChapterRepository(listOf(group("1", releaseCount = 2), group("2"))),
        )
        observe(viewModel.state)
        runCurrent()
        val targets = viewModel.state.value.readableTargets

        viewModel.selectFilter(ChapterListFilter.MULTI_RELEASE)
        runCurrent()

        assertEquals(targets, viewModel.state.value.readableTargets)
        assertEquals(3, viewModel.state.value.readableTargets.size)
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
        assertEquals(emptyList(), viewModel.state.value.readableTargets)

        repository.replace(listOf(group("1")))
        runCurrent()

        assertEquals(
            listOf(ChapterReleaseId("release:1:0")),
            viewModel.state.value.readableTargets.map { it.releaseId },
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

        val releases = viewModel.state.value.chapters.single().releases
        assertEquals(listOf(false, true), releases.map { it.readerCapable })
        assertEquals(
            listOf(ChapterReleaseId("release:1:0"), ChapterReleaseId("release:1:1")),
            viewModel.state.value.releaseTargets.map { it.releaseId },
        )
        assertEquals(listOf(ChapterReleaseId("release:1:1")), viewModel.state.value.readableTargets.map { it.releaseId })
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

        val releases = viewModel.state.value.chapters.single().releases
        assertEquals(listOf(true, true), releases.map { it.readerCapable })
        assertEquals(listOf(false, true), releases.map { it.downloadCapable })
        assertEquals(
            listOf(ChapterReleaseId("release:1:0"), ChapterReleaseId("release:1:1")),
            viewModel.state.value.readableTargets.map { it.releaseId },
        )
        assertEquals(
            listOf(ChapterReleaseId("release:1:1")),
            viewModel.state.value.downloadableTargets.map { it.releaseId },
        )
    }

    @Test
    fun tombstonesStayHiddenUntilExplicitlyRequested() = runTest(dispatcher.scheduler) {
        val repository = FakeChapterRepository(listOf(group("1"), group("2", tombstoned = true)))
        val viewModel = chapterViewModel(repository)
        observe(viewModel.state)
        runCurrent()

        assertEquals(listOf(CanonicalChapterId("chapter:1")), viewModel.state.value.chapters.map { it.id })

        viewModel.setTombstonesVisible(true)
        runCurrent()

        assertEquals(2, viewModel.state.value.chapters.size)
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

        assertTrue(viewModel.state.value.refreshing)

        gate.complete(Unit)
        runCurrent()

        assertFalse(viewModel.state.value.refreshing)
        assertEquals(null, viewModel.state.value.failure)
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

        assertFalse(viewModel.state.value.refreshing)
        assertEquals(listOf(CanonicalChapterId("chapter:1")), viewModel.state.value.chapters.map { it.id })
        assertEquals("chapter.sync_failed", viewModel.state.value.failure)
    }

    private fun TestScope.observe(state: StateFlow<ChapterListUiState>) {
        backgroundScope.launch(dispatcher) { state.collect {} }
    }
}

private fun chapterViewModel(
    repository: ChapterRepository,
    readerPlugins: Set<PluginId> = setOf(PluginId("content.0"), PluginId("content.1")),
    offlineDownloadPlugins: Set<PluginId> = readerPlugins,
    syncService: ChapterSyncService = chapterSyncService(),
) = ChapterListViewModel(
    ChapterListAssistedArgs(STORY_ID),
    repository,
    object : ReaderSourceAvailability {
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

private class FakeChapterRepository(initial: List<CanonicalChapterGroup>) : ChapterRepository {
    private val groups = MutableStateFlow(initial)
    val savedOverrides = mutableListOf<ChapterAggregationOverride>()

    fun replace(value: List<CanonicalChapterGroup>) {
        groups.value = value
    }

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
