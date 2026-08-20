package app.openstory.catalog.ui.discover

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.details.CatalogDetailsLoader
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.metadata.CatalogMetadataCoordinator
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataPolicy
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun cachedLatestWithoutArtworkHydratesDetailsAndUpdatesProjection() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(coverUrl = null))
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(1, source.detailsCalls)
        assertEquals("https://example.test/source-1.jpg", viewModel.state.value.latestUpdates.single().coverUrl)
    }

    @Test
    fun cachedLatestWithArtworkDoesNotHydrateDetails() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(coverUrl = "https://example.test/cached.jpg"))
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(0, source.detailsCalls)
        assertEquals("https://example.test/cached.jpg", viewModel.state.value.latestUpdates.single().coverUrl)
    }

    @Test
    fun latestArtworkHydrationIsBoundedToFiveEntries() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(count = 6, coverUrl = null))
        val source = FakeSource()
        viewModel(repository, source)
        runCurrent()

        assertEquals(5, source.detailsCalls)
        assertEquals(
            listOf("source-1", "source-2", "source-3", "source-4", "source-5"),
            source.detailSourceIds,
        )
    }

    @Test
    fun negativeCachedArtworkDoesNotRefetchInsideArtworkTtl() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(coverUrl = null))
        val source = FakeSource().apply { detailsCoverUrl = null }
        val first = viewModel(repository, source)
        backgroundScope.launch { first.state.collect() }
        runCurrent()
        assertEquals(1, source.detailsCalls)

        val second = viewModel(repository, source)
        backgroundScope.launch { second.state.collect() }
        runCurrent()

        assertEquals(1, source.detailsCalls)
        assertEquals(null, second.state.value.latestUpdates.single().coverUrl)
    }

    @Test
    fun artworkFailureIsBestEffortAndKeepsCachedDiscoverContent() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(coverUrl = null))
        val source = FakeSource().apply {
            detailsFailure = CatalogSourceFailure("catalog.details.offline", true)
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(1, source.detailsCalls)
        assertEquals("Fixture Novel 1", viewModel.state.value.latestUpdates.single().title)
        assertEquals(null, viewModel.state.value.globalFailure)
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
        val repository = FakeRepository(cachedHome(), matchFailuresRemaining = 1)
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

    private fun TestScope.viewModel(repository: FakeRepository, source: FakeSource): DiscoverViewModel {
        val registry = Registry(source)
        val clock = FakeClock(200L)
        val matcher = StoryMatcher()
        val metadata = CatalogMetadataCoordinator(
            repository = repository,
            sources = registry,
            loader = CatalogDetailsLoader(registry, repository, matcher, clock),
            policy = CatalogMetadataPolicy(clock),
            clock = clock,
            processScope = backgroundScope,
        )
        return DiscoverViewModel(
            repository,
            CatalogRefreshService(registry, repository, matcher, clock),
            metadata,
            DiscoverProjectionPipeline(
                FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
            ),
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
    val detailSourceIds = mutableListOf<String>()
    var detailsCoverUrl: String? = "https://example.test/source.jpg"
    var detailsFailure: CatalogSourceFailure? = null
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
        detailSourceIds += sourceId
        detailsFailure?.let { return CatalogSourceResult.Failure(it) }
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
                coverUrl = detailsCoverUrl?.let { "https://example.test/$sourceId.jpg" },
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
    private var matchFailuresRemaining: Int = 0,
    private val observeFailure: Boolean = false,
    private val observeFailureAfterEmission: Boolean = false,
) : CatalogRepository {
    private val homes = MutableStateFlow(initialHomes)
    private val detailStamps = mutableMapOf<CatalogMetadataKey, CatalogMetadataStamp>()
    var observeHomesSubscriptions = 0
        private set

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
    override suspend fun matchSnapshot(): CatalogMatchSnapshot {
        if (matchFailuresRemaining > 0) {
            matchFailuresRemaining--
            error("catalog unavailable")
        }
        return CatalogMatchSnapshot(emptyList())
    }
    override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? {
        val home = homes.value.firstOrNull { it.pluginId == key.pluginId } ?: return null
        val entry = home.sections.asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.pluginId == key.pluginId && it.sourceId == key.sourceId }
            ?: return null
        val summary = CatalogMetadataStamp(home.pluginVersion, home.refreshedAtEpochMillis)
        val detail = detailStamps[key]
        val artwork = if (!entry.coverUrl.isNullOrBlank()) detail ?: summary else detail
        return CatalogMetadataSnapshot(entry, summary, artwork = artwork, full = detail)
    }

    override suspend fun commitHomeRefresh(
        mutation: CatalogHomeMutation,
    ): Outcome<Unit, CatalogStoreFailure> = Outcome.Success(Unit)
    override suspend fun commitDetails(
        mutation: CatalogDetailsMutation,
    ): Outcome<StoryId, CatalogStoreFailure> {
        val key = CatalogMetadataKey(mutation.entry.pluginId, mutation.entry.sourceId)
        detailStamps[key] = CatalogMetadataStamp(mutation.pluginVersion, mutation.fetchedAtEpochMillis)
        var durableStoryId = mutation.storyId
        homes.value = homes.value.map { home ->
            home.copy(
                sections = home.sections.map { section ->
                    section.copy(
                        items = section.items.map { existing ->
                            if (
                                existing.pluginId == mutation.entry.pluginId &&
                                existing.sourceId == mutation.entry.sourceId
                            ) {
                                durableStoryId = existing.storyId
                                mutation.entry.copy(
                                    storyId = existing.storyId,
                                    latestUpdate = mutation.entry.latestUpdate ?: existing.latestUpdate,
                                )
                            } else {
                                existing
                            }
                        },
                    )
                },
            )
        }
        return Outcome.Success(durableStoryId)
    }
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
