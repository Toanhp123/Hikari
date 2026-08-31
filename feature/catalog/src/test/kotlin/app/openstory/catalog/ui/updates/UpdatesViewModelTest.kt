package app.openstory.catalog.ui.updates

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.activity.LibraryActivityProjector
import app.openstory.catalog.ui.state.ContentState
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryMappingScheduler
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun emptyLibraryShortCircuitsToReadyEmptyWithoutOtherEmissions() = runTest(dispatcher) {
        val chapters = MutableSharedFlow<List<CanonicalChapterGroup>>()
        val mappings = MutableSharedFlow<List<ContentMapping>>()
        val catalog = MutableSharedFlow<List<CatalogStoryProjection>>()
        val viewModel = viewModel(
            libraryFlow = MutableStateFlow(emptyList()),
            chapterFlow = { chapters },
            mappingFlow = { mappings },
            catalogFlow = { catalog },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val content = assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content).value
        assertTrue(content.isEmpty)
        assertNull(viewModel.state.value.observationIssue)
        assertEquals(0, chapters.subscriptionCount.value)
        assertEquals(0, mappings.subscriptionCount.value)
        assertEquals(0, catalog.subscriptionCount.value)
    }

    @Test
    fun nonEmptyLibraryWaitsForChapterAndMappingMembershipFacts() = runTest(dispatcher) {
        val storyId = StoryId("story-a")
        val chapters = MutableSharedFlow<List<CanonicalChapterGroup>>()
        val mappings = MutableSharedFlow<List<ContentMapping>>()
        val viewModel = viewModel(
            libraryFlow = MutableStateFlow(listOf(entry(storyId))),
            chapterFlow = { chapters },
            mappingFlow = { mappings },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()
        assertIs<ContentState.Pending>(viewModel.state.value.content)

        backgroundScope.launch { chapters.emit(listOf(group(storyId))) }
        runCurrent()
        assertIs<ContentState.Pending>(viewModel.state.value.content)

        backgroundScope.launch { mappings.emit(listOf(mapping(storyId))) }
        runCurrent()
        assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content)
    }

    @Test
    fun catalogAndReaderAvailabilityDoNotBlockProjectedUpdates() = runTest(dispatcher) {
        val storyId = StoryId("story-a")
        val catalog = MutableSharedFlow<List<CatalogStoryProjection>>()
        val viewModel = viewModel(
            libraryFlow = MutableStateFlow(listOf(entry(storyId))),
            chapterFlow = { flowOf(listOf(group(storyId))) },
            mappingFlow = { flowOf(listOf(mapping(storyId))) },
            catalogFlow = { catalog },
            readerSources = ReaderSourceAvailability { throw IllegalStateException("reader unavailable") },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val content = assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content).value
        val item = content.groups.single().items.single()
        assertEquals(storyId.value, item.title)
        assertNull(item.readerTarget)
        assertEquals("updates.reader.observe_failed", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun storyIdKeyChangeCannotReuseOldChapterOrMappingValues() = runTest(dispatcher) {
        val storyA = StoryId("story-a")
        val storyB = StoryId("story-b")
        val library = MutableStateFlow(listOf(entry(storyA)))
        val chapterA = MutableStateFlow(listOf(group(storyA)))
        val mappingA = MutableStateFlow(listOf(mapping(storyA)))
        val chapterB = MutableSharedFlow<List<CanonicalChapterGroup>>()
        val mappingB = MutableSharedFlow<List<ContentMapping>>()
        val viewModel = viewModel(
            libraryFlow = library,
            chapterFlow = { key -> if (key == setOf(storyA)) chapterA else chapterB },
            mappingFlow = { key -> if (key == setOf(storyA)) mappingA else mappingB },
        )
        val observed = mutableListOf<UpdatesUiState>()
        backgroundScope.launch { viewModel.state.collect { observed += it } }
        runCurrent()
        assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content)

        val beforeChange = observed.size
        library.value = listOf(entry(storyB))
        runCurrent()

        assertIs<ContentState.Pending>(viewModel.state.value.content)
        val afterChange = observed.drop(beforeChange)
        assertTrue(afterChange.none { it.content is ContentState.Ready })
    }

    @Test
    fun firstRequiredFailureIsBlocking() = runTest(dispatcher) {
        val storyId = StoryId("story-a")
        val viewModel = viewModel(
            libraryFlow = MutableStateFlow(listOf(entry(storyId))),
            chapterFlow = { flow { throw IllegalStateException("chapters unavailable") } },
            mappingFlow = { flowOf(listOf(mapping(storyId))) },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val failed = assertIs<ContentState.Failed>(viewModel.state.value.content)
        assertEquals("updates.chapters.observe_failed", failed.failure.code)
        assertTrue(failed.failure.retryable)
        assertNull(viewModel.state.value.observationIssue)
    }

    @Test
    fun postValueRequiredFailureRetainsUpdatesAndIssue() = runTest(dispatcher) {
        val storyId = StoryId("story-a")
        val viewModel = viewModel(
            libraryFlow = MutableStateFlow(listOf(entry(storyId))),
            chapterFlow = {
                flow {
                    emit(listOf(group(storyId)))
                    throw IllegalStateException("chapters unavailable")
                }
            },
            mappingFlow = { flowOf(listOf(mapping(storyId))) },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val content = assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content).value
        assertEquals(storyId, content.groups.single().items.single().storyId)
        assertEquals("updates.chapters.observe_failed", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun catalogFailureRetainsFallbackTitle() = runTest(dispatcher) {
        val storyId = StoryId("story-a")
        val viewModel = viewModel(
            libraryFlow = MutableStateFlow(listOf(entry(storyId))),
            chapterFlow = { flowOf(listOf(group(storyId))) },
            mappingFlow = { flowOf(listOf(mapping(storyId))) },
            catalogFlow = { flow { throw IllegalStateException("catalog unavailable") } },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val content = assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content).value
        assertEquals(storyId.value, content.groups.single().items.single().title)
        assertEquals("updates.catalog.observe_failed", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun retryContentRestartsOnlyUnavailableRequiredInputs() = runTest(dispatcher) {
        val storyId = StoryId("story-a")
        var libraryAttempts = 0
        var chapterAttempts = 0
        var mappingAttempts = 0
        var catalogAttempts = 0
        var readerAttempts = 0
        val viewModel = viewModel(
            libraryFlowFactory = {
                libraryAttempts += 1
                flowOf(listOf(entry(storyId)))
            },
            chapterFlow = {
                chapterAttempts += 1
                if (chapterAttempts == 1) {
                    flow { throw IllegalStateException("chapters unavailable") }
                } else {
                    flowOf(listOf(group(storyId)))
                }
            },
            mappingFlow = {
                mappingAttempts += 1
                if (mappingAttempts == 1) {
                    flow { throw IllegalStateException("mappings unavailable") }
                } else {
                    flowOf(listOf(mapping(storyId)))
                }
            },
            catalogFlow = {
                catalogAttempts += 1
                flowOf(listOf(projection(storyId)))
            },
            readerSources = ReaderSourceAvailability {
                readerAttempts += 1
                setOf(PluginId("content.fixture"))
            },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()
        val failed = assertIs<ContentState.Failed>(viewModel.state.value.content)
        assertEquals("updates.chapters.observe_failed", failed.failure.code)

        viewModel.retryContent()
        runCurrent()

        assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content)
        assertEquals(1, libraryAttempts)
        assertEquals(2, chapterAttempts)
        assertEquals(2, mappingAttempts)
        assertEquals(1, catalogAttempts)
        assertEquals(1, readerAttempts)
    }

    @Test
    fun retryObservationTargetsCurrentKeyIssueAndNeverRetriesStaleKeyFailure() = runTest(dispatcher) {
        val storyA = StoryId("story-a")
        val storyB = StoryId("story-b")
        val library = MutableStateFlow(listOf(entry(storyA)))
        var chapterAAttempts = 0
        var chapterBAttempts = 0
        val chapterB = MutableStateFlow(listOf(group(storyB)))
        val viewModel = viewModel(
            libraryFlow = library,
            chapterFlow = { key ->
                when (key) {
                    setOf(storyA) -> {
                        chapterAAttempts += 1
                        flow {
                            emit(listOf(group(storyA)))
                            throw IllegalStateException("stale A issue")
                        }
                    }
                    setOf(storyB) -> {
                        chapterBAttempts += 1
                        chapterB
                    }
                    else -> flowOf(emptyList())
                }
            },
            mappingFlow = { key -> flowOf(key.map(::mapping)) },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()
        assertEquals("updates.chapters.observe_failed", viewModel.state.value.observationIssue?.code)

        library.value = listOf(entry(storyB))
        runCurrent()
        assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content)
        assertNull(viewModel.state.value.observationIssue)

        viewModel.retryObservation()
        runCurrent()

        assertEquals(1, chapterAAttempts)
        assertEquals(1, chapterBAttempts)
    }

    @Test
    fun libraryFailureBlocksAndRetryContentRestartsLibraryOnly() = runTest(dispatcher) {
        var libraryAttempts = 0
        var chapterAttempts = 0
        var mappingAttempts = 0
        val viewModel = viewModel(
            libraryFlowFactory = {
                libraryAttempts += 1
                if (libraryAttempts == 1) {
                    flow { throw IllegalStateException("library unavailable") }
                } else {
                    flowOf(emptyList())
                }
            },
            chapterFlow = {
                chapterAttempts += 1
                flowOf(emptyList())
            },
            mappingFlow = {
                mappingAttempts += 1
                flowOf(emptyList())
            },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val failed = assertIs<ContentState.Failed>(viewModel.state.value.content)
        assertEquals("updates.library.observe_failed", failed.failure.code)

        viewModel.retryContent()
        runCurrent()

        val content = assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content).value
        assertTrue(content.isEmpty)
        assertEquals(2, libraryAttempts)
        assertEquals(0, chapterAttempts)
        assertEquals(0, mappingAttempts)
    }

    @Test
    fun catalogArrivalEnrichesReadyContentWithoutReturningToPending() = runTest(dispatcher) {
        val storyId = StoryId("story-a")
        val catalog = MutableSharedFlow<List<CatalogStoryProjection>>()
        val viewModel = viewModel(
            libraryFlow = MutableStateFlow(listOf(entry(storyId))),
            chapterFlow = { flowOf(listOf(group(storyId))) },
            mappingFlow = { flowOf(listOf(mapping(storyId))) },
            catalogFlow = { catalog },
        )
        val states = mutableListOf<UpdatesUiState>()
        backgroundScope.launch { viewModel.state.collect { states += it } }
        runCurrent()
        assertTrue(catalog.subscriptionCount.value > 0)

        val firstReady = assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content)
        assertEquals(storyId.value, firstReady.value.groups.single().items.single().title)

        backgroundScope.launch { catalog.emit(listOf(projection(storyId))) }
        runCurrent()

        val lastReady = assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content)
        assertEquals("Title ${storyId.value}", lastReady.value.groups.single().items.single().title)
        assertTrue(states.dropWhile { it.content !is ContentState.Ready }.none { it.content is ContentState.Pending })
    }

    @Test
    fun retryObservationRestartsSurfacedCatalogIssueOnly() = runTest(dispatcher) {
        val storyId = StoryId("story-a")
        var catalogAttempts = 0
        var chapterAttempts = 0
        val viewModel = viewModel(
            libraryFlow = MutableStateFlow(listOf(entry(storyId))),
            chapterFlow = {
                chapterAttempts += 1
                flowOf(listOf(group(storyId)))
            },
            mappingFlow = { flowOf(listOf(mapping(storyId))) },
            catalogFlow = {
                catalogAttempts += 1
                if (catalogAttempts == 1) {
                    flow { throw IllegalStateException("catalog unavailable") }
                } else {
                    flowOf(listOf(projection(storyId)))
                }
            },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()
        assertEquals("updates.catalog.observe_failed", viewModel.state.value.observationIssue?.code)

        viewModel.retryObservation()
        runCurrent()

        val content = assertIs<ContentState.Ready<UpdatesContent>>(viewModel.state.value.content).value
        assertEquals("Title ${storyId.value}", content.groups.single().items.single().title)
        assertNull(viewModel.state.value.observationIssue)
        assertEquals(2, catalogAttempts)
        assertEquals(1, chapterAttempts)
    }

    private fun viewModel(
        libraryFlow: Flow<List<LibraryEntry>> = MutableStateFlow(emptyList()),
        libraryFlowFactory: (() -> Flow<List<LibraryEntry>>)? = null,
        chapterFlow: (Set<StoryId>) -> Flow<List<CanonicalChapterGroup>> = { flowOf(emptyList()) },
        mappingFlow: (Set<StoryId>) -> Flow<List<ContentMapping>> = { flowOf(emptyList()) },
        catalogFlow: (Set<StoryId>) -> Flow<List<CatalogStoryProjection>> = { flowOf(emptyList()) },
        readerSources: ReaderSourceAvailability = ReaderSourceAvailability { setOf(PluginId("content.fixture")) },
    ): UpdatesViewModel {
        val libraryRepository = object : LibraryRepository {
            override fun observe(): Flow<List<LibraryEntry>> = libraryFlowFactory?.invoke() ?: libraryFlow
            override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long) = error("unused")
            override suspend fun remove(storyId: StoryId) = Unit
            override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long) = null
        }
        return UpdatesViewModel(
            library = LibraryService(libraryRepository, { 1L }, LibraryMappingScheduler {}),
            catalog = FakeCatalogRepository(catalogFlow),
            chapters = FakeChapterRepository(chapterFlow),
            mappings = FakeMappingRepository(mappingFlow),
            readerSources = readerSources,
            projector = LibraryActivityProjector(),
        )
    }
}

private class FakeCatalogRepository(
    private val source: (Set<StoryId>) -> Flow<List<CatalogStoryProjection>>,
) : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(emptyList())
    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<CatalogStoryProjection>> = source(storyIds)
}

private class FakeChapterRepository(
    private val source: (Set<StoryId>) -> Flow<List<CanonicalChapterGroup>>,
) : ChapterRepository {
    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = flowOf(emptyList())
    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<CanonicalChapterGroup>> = source(storyIds)
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> = flowOf(emptyList())
    override suspend fun snapshot(storyId: StoryId) = error("unused")
    override suspend fun commit(mutation: app.openstory.chapters.repository.ChapterMutation) = error("unused")
    override suspend fun saveOverride(
        storyId: StoryId,
        override: app.openstory.chapters.model.ChapterAggregationOverride,
    ) = error("unused")
    override suspend fun syncState(storyId: StoryId, pluginId: PluginId, sourceStoryId: String) = null
}

private class FakeMappingRepository(
    private val source: (Set<StoryId>) -> Flow<List<ContentMapping>>,
) : ContentMappingRepository {
    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> = source(setOf(storyId))
    override fun observeAll(): Flow<List<ContentMapping>> = flowOf(emptyList())
    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<ContentMapping>> = source(storyIds)
    override suspend fun compareAndWrite(
        mapping: ContentMapping,
        replaceableOrigins: Set<ContentMappingOrigin>,
    ) = ContentMappingWriteResult.Written(mapping, true)
    override suspend fun reject(rejection: ContentMappingRejection) = Unit
    override suspend fun isRejected(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
        policyVersion: Int,
    ) = false
}

private fun StateFlow<*>.collectForTest(scope: kotlinx.coroutines.CoroutineScope) =
    scope.launch { collect {} }

private fun entry(storyId: StoryId) = LibraryEntry(storyId, LibraryStatus.READING, 1L, 2L)

private fun projection(storyId: StoryId) =
    CatalogStoryProjection(storyId, "Title ${storyId.value}", ContentType.WEB_NOVEL, null)

private fun mapping(storyId: StoryId) = ContentMapping(
    storyId = storyId,
    pluginId = PluginId("content.fixture"),
    sourceStoryId = "source-${storyId.value}",
    origin = ContentMappingOrigin.AUTOMATED,
    policyVersion = 1,
    updatedAt = 1L,
)

private fun group(storyId: StoryId): CanonicalChapterGroup {
    val suffix = storyId.value
    val chapterId = CanonicalChapterId("chapter-$suffix")
    val releaseId = ChapterReleaseId("release-$suffix")
    val parsed = ParsedChapterLabel(ChapterKind.NUMBERED, null, BigDecimal.ONE, null, null)
    val release = ChapterRelease(
        id = releaseId,
        storyId = storyId,
        pluginId = PluginId("content.fixture"),
        sourceStoryId = "source-${storyId.value}",
        sourceReleaseId = "source-release-$suffix",
        displayLabel = "Chapter 1",
        parsedLabel = parsed,
        languageTag = "en",
        publishedAtEpochMillis = 1_754_236_800_000L,
        canonicalChapterId = chapterId,
    )
    return CanonicalChapterGroup(
        chapter = CanonicalChapter(
            id = chapterId,
            storyId = storyId,
            parsedLabel = parsed,
            displayLabel = "Chapter 1",
            tombstoned = false,
            releaseIds = setOf(releaseId),
        ),
        releases = listOf(release),
    )
}
