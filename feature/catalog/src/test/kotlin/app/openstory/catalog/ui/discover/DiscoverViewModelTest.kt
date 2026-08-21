package app.openstory.catalog.ui.discover

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceFailure
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.FakeClock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun homesAndRankingShareOneRepositoryObservation() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val viewModel = viewModel(repository, FakeSource())
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(1, repository.observeHomesSubscriptions)
    }

    @Test
    fun refreshUsesCommittedSuccessTimestampWithoutSecondHomeObservation() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val viewModel = viewModel(repository, FakeSource())
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals(1, repository.observeHomesSubscriptions)
        assertEquals(
            200L,
            viewModel.state.value.refreshReport?.refreshedAtEpochMillis?.get(PluginId("catalog.a")),
        )
    }

    @Test
    fun emptyFirstCacheEmissionBootstrapsRefreshExactlyOnce() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource()

        viewModel(repository, source)
        runCurrent()

        assertEquals(1, source.homeCalls)
        runCurrent()
        assertEquals(1, source.homeCalls)
    }

    @Test
    fun populatedFirstCacheEmissionDoesNotBootstrapRefresh() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()

        viewModel(repository, source)
        runCurrent()

        assertEquals(0, source.homeCalls)
    }

    @Test
    fun populatedMigratedCacheBootstrapsVisibleCanonicalStoriesWithoutNetwork() =
        runTest(dispatcher.scheduler) {
            val repository = FakeRepository(cachedHome())
            val source = FakeSource()
            val projections = MutableProjectionRepository(emptyList())
            val canonical = DiscoverCanonicalRepository(preparingDiscoverState(StoryId("story-1")))
            val rebuildCalls = mutableListOf<StoryId>()
            val bootstrap = CanonicalBootstrapUseCase(
                canonical,
                CanonicalGenerationRebuilder { storyId, _ ->
                    rebuildCalls += storyId
                    projections.replace(repository.projections())
                    CanonicalFusionResult.Preparing(storyId)
                },
            )
            val viewModel = viewModel(
                repository = repository,
                source = source,
                bootstrap = bootstrap,
                projections = projections,
            )
            backgroundScope.launch { viewModel.state.collect() }

            runCurrent()

            assertEquals(listOf(StoryId("story-1")), rebuildCalls)
            assertEquals("Fixture Novel", viewModel.state.value.popular.single().title)
            assertEquals(0, source.homeCalls)
            assertEquals(0, source.detailsCalls)
        }

    @Test
    fun cachedLatestWithoutArtworkDoesNotCallDetailsAndKeepsMissingArtwork() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(coverUrl = null))
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(0, source.detailsCalls)
        assertEquals(null, viewModel.state.value.latestUpdates.single().coverUrl)
    }

    @Test
    fun cachedLatestWithArtworkUsesPluginListingValueWithoutDetails() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(coverUrl = "https://example.test/cached.jpg"))
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(0, source.detailsCalls)
        assertEquals("https://example.test/cached.jpg", viewModel.state.value.latestUpdates.single().coverUrl)
    }

    @Test
    fun manualRefreshDoesNotUseDetailsToRepairMissingArtwork() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(coverUrl = null))
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals(1, source.homeCalls)
        assertEquals(0, source.detailsCalls)
        assertEquals(null, viewModel.state.value.latestUpdates.single().coverUrl)
    }

    @Test
    fun failedEmptyCacheBootstrapDoesNotRetryLoop() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource().apply {
            homeAction = {
                CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true))
            }
        }

        viewModel(repository, source)
        runCurrent()
        assertEquals(1, source.homeCalls)

        runCurrent()
        assertEquals(1, source.homeCalls)
    }

    @Test
    fun observationFailureFallbackDoesNotBootstrapNetworkRefresh() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList(), observeFailure = true)
        val source = FakeSource()

        viewModel(repository, source)
        runCurrent()

        assertEquals(0, source.homeCalls)
    }

    @Test
    fun mangaIsDefaultAndLightNovelSelectionIsIgnoredWhileDisabled() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(ContentType.MANGA, viewModel.state.value.selectedContentType)

        viewModel.selectContentType(ContentType.LIGHT_NOVEL)
        runCurrent()

        assertEquals(ContentType.MANGA, viewModel.state.value.selectedContentType)
        assertEquals(0, source.homeCalls)
    }

    @Test
    fun emptyCacheKeepsInitialLoadingUntilBootstrapRefreshFinishes() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource()
        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertTrue(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.refreshing)

        release.complete(Unit)
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertFalse(viewModel.state.value.refreshing)
        assertTrue(viewModel.state.value.popular.isEmpty())
        assertTrue(viewModel.state.value.latestUpdates.isEmpty())
        assertTrue(viewModel.state.value.topRated.isEmpty())
    }

    @Test
    fun refreshFlagsDoNotReprojectSemanticLists() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()
        val popular = viewModel.state.value.popular

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refreshing)
        assertSame(popular, viewModel.state.value.popular)

        release.complete(Unit)
        runCurrent()

        assertSame(popular, viewModel.state.value.popular)
    }

    @Test
    fun cachedContentNeverReturnsToSkeletonDuringManualRefresh() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals("Fixture Novel", viewModel.state.value.popular.single().title)

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refreshing)
        assertFalse(viewModel.state.value.loading)
        assertEquals("Fixture Novel", viewModel.state.value.popular.single().title)

        release.complete(Unit)
        runCurrent()
    }

    @Test
    fun cachedHomeEmitsBeforeRefreshCompletes() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refreshing)
        assertEquals("Fixture Novel", viewModel.state.value.popular.single().title)
        release.complete(Unit)
        runCurrent()
        assertFalse(viewModel.state.value.refreshing)
    }

    @Test
    fun refreshFailureKeepsCachedSectionsAndExposesNonBlockingFailure() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource().apply {
            homeAction = {
                CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true))
            }
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals("Fixture Novel", viewModel.state.value.popular.single().title)
        assertEquals("catalog.offline", viewModel.state.value.refreshReport?.failed?.get(source.pluginId))
    }

    @Test
    fun refreshWorkUsesFeatureDefaultSchedulingBoundary() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val refreshScheduler = TestCoroutineScheduler()
        val refreshDispatcher = StandardTestDispatcher(refreshScheduler)
        val viewModel = viewModel(repository, source, refreshDispatcher)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refreshing)
        assertEquals(0, source.homeCalls)

        refreshScheduler.runCurrent()
        runCurrent()

        assertEquals(1, source.homeCalls)
        assertFalse(viewModel.state.value.refreshing)
    }

    @Test
    fun refreshActionInvokesCatalogRefreshServiceExactlyOnce() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals(1, source.homeCalls)
    }

    @Test
    fun refreshBoundaryExceptionBecomesNonBlockingFailure() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome(), sourceRecordFailuresRemaining = 1)
        val viewModel = viewModel(repository, FakeSource())
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertFalse(viewModel.state.value.refreshing)
        assertEquals("catalog.home.refresh_exception", viewModel.state.value.globalFailure?.code)
        assertEquals("Fixture Novel", viewModel.state.value.popular.single().title)
    }

    @Test
    fun observationFailureBeforeFirstEmissionIsVisible() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList(), observeFailure = true)
        val viewModel = viewModel(repository, FakeSource())
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertTrue(
            viewModel.state.value.globalFailure?.code in setOf(
                "catalog.home.observe_exception",
                "catalog.home.ranking_exception",
            ),
        )
        assertFalse(viewModel.state.value.hasContent)
    }

    @Test
    fun observationFailureAfterCachedEmissionRetainsCache() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome(), observeFailureAfterEmission = true)
        val viewModel = viewModel(repository, FakeSource())
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals("Fixture Novel", viewModel.state.value.popular.single().title)
        assertTrue(viewModel.state.value.globalFailure != null)

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.observationFailure != null)
        assertEquals(null, viewModel.state.value.refreshFailure)
    }

    @Test
    fun discoverReadPathHasNoDetailsOrFusionDependency() {
        val dependencies = DiscoverViewModel::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.name }
        assertFalse(dependencies.any { it.endsWith("CatalogMetadataCoordinator") })
        assertFalse(dependencies.any { it.endsWith("CatalogDetailsLoader") })
        assertFalse(dependencies.any { it.endsWith("CatalogFusionEngine") })
        assertFalse(dependencies.any { it.endsWith("CanonicalFusionService") })
    }

    private fun TestScope.viewModel(
        repository: FakeRepository,
        source: FakeSource,
        refreshDispatcher: CoroutineDispatcher = dispatcher,
        bootstrap: CanonicalBootstrapUseCase = readyBootstrap(repository),
        projections: CatalogStoryProjectionRepository = FakeProjectionRepository(repository.projections()),
    ): DiscoverViewModel {
        val registry = Registry(source)
        val clock = FakeClock(200L)
        val refreshService = CatalogRefreshService(
            sources = registry,
            repository = repository,
            reconciliationEngine = app.openstory.catalog.reconciliation.CatalogReconciliationEngine(
                app.openstory.catalog.reconciliation.ReconciliationPolicy(),
            ),
            storyIdFactory = app.openstory.catalog.identity.CatalogStoryIdFactory(),
            reconciliation = app.openstory.catalog.featureTestReconciliationService(repository, clock),
            fusion = app.openstory.catalog.featureNoOpCanonicalRebuilder,
            clock = clock,
        )
        return DiscoverViewModel(
            repository,
            projections,
            DiscoverRefreshPipeline(
                refreshService,
                FixedAppDispatchers(dispatcher, refreshDispatcher, dispatcher),
            ),
            DiscoverProjectionPipeline(
                FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
            ),
            DiscoverCanonicalBootstrapPipeline(
                bootstrap,
                FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
            ),
        )
    }

    private fun readyBootstrap(repository: FakeRepository): CanonicalBootstrapUseCase {
        val states = repository.projections().map { projection ->
            readyDiscoverState(projection.storyId)
        }
        val canonical = DiscoverCanonicalRepository(states)
        return CanonicalBootstrapUseCase(
            canonical,
            CanonicalGenerationRebuilder { storyId, _ -> CanonicalFusionResult.Preparing(storyId) },
        )
    }
}

private class Registry(private val sourceValue: CatalogSource) : CatalogSourceRegistry {
    override suspend fun enabled() = listOf(sourceValue)
    override suspend fun source(pluginId: PluginId) = sourceValue.takeIf { it.pluginId == pluginId }
}

private class FakeSource : CatalogSource {
    override val pluginId = PluginId("catalog.a")
    override val version = "1.0.0"
    var homeCalls = 0
    var detailsCalls = 0
    var homeAction: suspend () -> CatalogSourceResult<List<SourceSection>> = {
        CatalogSourceResult.Success(emptyList())
    }

    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> {
        homeCalls++
        return homeAction()
    }

    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> =
        error("unused")
    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> {
        detailsCalls++
        return CatalogSourceResult.Success(
            SourceDetails(
                sourceId = sourceId,
                sourceUrl = "https://example.test/$sourceId",
                title = "Fixture Novel $sourceId",
                aliases = emptySet(),
                authors = setOf("Fixture Author"),
                description = "Hydrated details",
                genres = setOf("Fantasy"),
                contentType = app.openstory.catalog.source.SourceContentType.MANGA,
                languageTags = setOf("en"),
                coverUrl = "https://example.test/$sourceId.jpg",
                scoreValue = 8.4,
                scoreScale = 10.0,
                popularityRank = 1L,
            ),
        )
    }
    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("unused")
}

private class FakeRepository(
    initialHomes: List<CatalogHomeSnapshot>,
    private var sourceRecordFailuresRemaining: Int = 0,
    private val observeFailure: Boolean = false,
    private val observeFailureAfterEmission: Boolean = false,
) : CatalogRepository {
    private val homes = MutableStateFlow(initialHomes)
    var observeHomesSubscriptions = 0
        private set

    fun projections(): List<CatalogStoryProjection> = homes.value
        .flatMap { it.sections }
        .flatMap { it.items }
        .distinctBy { it.storyId }
        .map { entry ->
            CatalogStoryProjection(
                storyId = entry.storyId,
                title = entry.title,
                contentType = entry.contentType,
                coverUrl = entry.coverUrl,
                authors = entry.authors,
                publicationStatus = entry.publicationStatus,
                latestUpdate = entry.latestUpdate,
                score = entry.score?.let { score ->
                    app.openstory.catalog.canonical.CanonicalScore(score.value / score.scale, 1)
                },
            )
        }

    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = flow {
        observeHomesSubscriptions++
        when {
            observeFailure -> error("catalog observation unavailable")
            observeFailureAfterEmission -> {
                emit(homes.value)
                error("catalog observation unavailable")
            }
            else -> homes.collect { emit(it) }
        }
    }
    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun matchSnapshot(): CatalogMatchSnapshot = CatalogMatchSnapshot(emptyList())
    override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = null

    override suspend fun sourceRecord(key: CatalogMetadataKey): app.openstory.catalog.evidence.CatalogSourceRecord? = null

    override suspend fun sourceRecords(storyId: StoryId): List<app.openstory.catalog.evidence.CatalogSourceRecord> = emptyList()

    override suspend fun sourceRecords(): List<app.openstory.catalog.evidence.CatalogSourceRecord> {
        if (sourceRecordFailuresRemaining > 0) {
            sourceRecordFailuresRemaining--
            error("catalog unavailable")
        }
        return emptyList()
    }

    override suspend fun commitHomeRefresh(
        mutation: CatalogHomeMutation,
    ): Outcome<app.openstory.catalog.repository.CatalogHomeCommitResult, CatalogStoreFailure> = Outcome.Success(app.openstory.catalog.repository.CatalogHomeCommitResult(emptyList()))

    override suspend fun commitSearchSummaries(
        mutation: app.openstory.catalog.repository.CatalogSearchSummaryMutation,
    ) = app.openstory.common.Outcome.Failure(
        app.openstory.catalog.CatalogStoreFailure("test.search.unsupported", retryable = false),
    )

    override suspend fun commitDetails(
        mutation: CatalogDetailsMutation,
    ): Outcome<app.openstory.catalog.repository.CatalogDetailsCommitResult, CatalogStoreFailure> = error("Discover must not commit Details")
}

private class FakeProjectionRepository(
    private val projections: List<CatalogStoryProjection>,
) : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(projections)
}

private fun cachedHome(pluginId: String = "catalog.a"): List<CatalogHomeSnapshot> {
    val pluginId = PluginId(pluginId)
    val storyId = StoryId("story-1")
    val entry = CatalogEntry(
        storyId = storyId,
        pluginId = pluginId,
        sourceId = "source-1",
        title = "Fixture Novel",
        authors = setOf("Fixture Author"),
        contentType = ContentType.MANGA,
        score = Score(8.4, 10.0),
    )
    return listOf(
        CatalogHomeSnapshot(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 100L,
            sections = listOf(
                CatalogHomeSection(
                    sourceId = "trending",
                    title = "Trending",
                    items = listOf(entry),
                    kind = CatalogFeedKind.POPULAR,
                ),
            ),
        ),
    )
}

private fun latestHome(
    pluginId: String = "catalog.a",
    count: Int = 1,
    coverUrl: String?,
): List<CatalogHomeSnapshot> {
    val pluginId = PluginId(pluginId)
    val items = (1..count).map { index ->
        CatalogEntry(
            storyId = StoryId("story-$index"),
            pluginId = pluginId,
            sourceId = "source-$index",
            title = "Fixture Novel $index",
            authors = setOf("Fixture Author"),
            contentType = ContentType.MANGA,
            coverUrl = coverUrl,
            latestUpdate = app.openstory.catalog.model.CatalogLatestUpdate(
                atEpochMillis = 1_000L - index,
                releaseLabel = index.toString(),
            ),
        )
    }
    return listOf(
        CatalogHomeSnapshot(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 100L,
            sections = listOf(
                CatalogHomeSection(
                    sourceId = "latest",
                    title = "Latest",
                    items = items,
                    kind = CatalogFeedKind.LATEST_UPDATES,
                ),
            ),
        ),
    )
}
