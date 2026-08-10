package app.openstory.catalog.ui.home

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.home.CatalogHomeQuery
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.model.CatalogEntry
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
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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
        assertEquals("Trending", viewModel.state.value.catalogs.single().sections.single().title)
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

        assertEquals("Trending", viewModel.state.value.catalogs.single().sections.single().title)
        assertEquals("catalog.offline", viewModel.state.value.refreshReport?.failed?.get(source.pluginId))
    }

    @Test
    fun sourceSelectionChangesProjectionWithoutPluginCall() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.selectCatalog(source.pluginId)
        runCurrent()

        assertEquals(source.pluginId, viewModel.state.value.selectedCatalog?.pluginId)
        assertEquals(0, source.homeCalls)
        viewModel.selectCombined()
        assertEquals(null, viewModel.state.value.selectedCatalogId)
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

    private fun viewModel(repository: FakeRepository, source: FakeSource) = HomeViewModel(
        repository,
        CatalogHomeQuery(repository),
        CatalogRefreshService(Registry(source), repository, StoryMatcher(), FakeClock(200L)),
    )
}

private class Registry(private val sourceValue: CatalogSource) : CatalogSourceRegistry {
    override suspend fun enabled() = listOf(sourceValue)
    override suspend fun source(pluginId: PluginId) = sourceValue.takeIf { it.pluginId == pluginId }
}

private class FakeSource : CatalogSource {
    override val pluginId = PluginId("catalog.a")
    override val version = "1.0.0"
    var homeCalls = 0
    var homeAction: suspend () -> CatalogSourceResult<List<SourceSection>> = {
        CatalogSourceResult.Success(emptyList())
    }

    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> {
        homeCalls++
        return homeAction()
    }

    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> =
        error("unused")
    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> = error("unused")
    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("unused")
}

private class FakeRepository(initialHomes: List<CatalogHomeSnapshot>) : CatalogRepository {
    private val homes = MutableStateFlow(initialHomes)
    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = homes
    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
    override suspend fun commitHomeRefresh(
        mutation: CatalogHomeMutation,
    ): Outcome<Unit, CatalogStoreFailure> = Outcome.Success(Unit)
    override suspend fun commitDetails(
        mutation: CatalogDetailsMutation,
    ): Outcome<StoryId, CatalogStoreFailure> = Outcome.Success(mutation.storyId)
}

private fun cachedHome(): List<CatalogHomeSnapshot> {
    val pluginId = PluginId("catalog.a")
    val storyId = StoryId("story-1")
    val entry = CatalogEntry(
        storyId = storyId,
        pluginId = pluginId,
        sourceId = "source-1",
        title = "Fixture Novel",
        authors = setOf("Fixture Author"),
        contentType = ContentType.WEB_NOVEL,
        score = Score(8.4, 10.0),
    )
    return listOf(
        CatalogHomeSnapshot(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 100L,
            sections = listOf(CatalogHomeSection("trending", "Trending", listOf(entry))),
        ),
    )
}
