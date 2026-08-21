package app.openstory.catalog.ui.search

import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalSourceSummary
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogSearchSummaryCommitResult
import app.openstory.catalog.repository.CatalogSearchSummaryMutation
import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.details.CatalogDetailsLoader
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.metadata.CatalogMetadataCoordinator
import app.openstory.catalog.metadata.CatalogMetadataPolicy
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.search.CatalogFilterCache
import app.openstory.catalog.search.CatalogSearchService
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceFailure
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceFilterOption
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceItem
import app.openstory.catalog.source.SourceOptionFilter
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.Outcome
import app.openstory.common.Clock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
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
    fun queryUnderMinimumLengthDoesNotExecuteSearch() = runTest(dispatcher.scheduler) {
        val source = FakeSearchSource("catalog.a")
        val viewModel = viewModel(source)

        viewModel.updateQuery("a")
        advanceSearch()

        assertEquals(0, source.searchCalls)
        assertTrue(viewModel.state.value.stories.isEmpty())
    }

    @Test
    fun newQueryObsoletesPreviousResult() = runTest(dispatcher.scheduler) {
        val firstRelease = CompletableDeferred<Unit>()
        val source = FakeSearchSource("catalog.a").apply {
            searchAction = { request ->
                if (request.query == "first") firstRelease.await()
                successPage(request.query)
            }
        }
        val viewModel = viewModel(source)

        viewModel.updateQuery("first")
        advanceSearch()
        viewModel.updateQuery("second")
        advanceSearch()
        firstRelease.complete(Unit)
        runCurrent()

        assertEquals("second", viewModel.state.value.stories.single().sources.single().title)
    }

    @Test
    fun newQueryCancelsActiveSearchBeforeNextDebounceCompletes() = runTest(dispatcher.scheduler) {
        val firstStarted = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        val source = FakeSearchSource("catalog.a").apply {
            searchAction = { request ->
                if (request.query == "first") {
                    firstStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        firstCancelled.complete(Unit)
                    }
                }
                successPage(request.query)
            }
        }
        val viewModel = viewModel(source)
        viewModel.updateQuery("first")
        advanceSearch()
        firstStarted.await()

        viewModel.updateQuery("second")
        runCurrent()

        assertTrue(firstCancelled.isCompleted)
        assertEquals(1, source.searchCalls)
    }

    @Test
    fun perSourceFilterStateRemainsSourceScoped() = runTest(dispatcher.scheduler) {
        val sourceA = FakeSearchSource("catalog.a")
        val sourceB = FakeSearchSource("catalog.b")
        val viewModel = viewModel(sourceA, sourceB)
        runCurrent()

        viewModel.setFilterValues(sourceA.pluginId, "genre", listOf("fantasy"))
        viewModel.setFilterValues(sourceB.pluginId, "status", listOf("complete"))
        viewModel.updateQuery("novel")
        advanceSearch()

        assertEquals(mapOf("genre" to listOf("fantasy")), sourceA.lastRequest?.filterValues)
        assertEquals(mapOf("status" to listOf("complete")), sourceB.lastRequest?.filterValues)
    }

    @Test
    fun partialSourceFailuresCoexistWithSuccessfulResults() = runTest(dispatcher.scheduler) {
        val success = FakeSearchSource("catalog.a")
        val failure = FakeSearchSource("catalog.b").apply {
            searchAction = { CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true)) }
        }
        val viewModel = viewModel(success, failure)

        viewModel.updateQuery("novel")
        advanceSearch()

        assertEquals(1, viewModel.state.value.stories.size)
        assertEquals("catalog.offline", viewModel.state.value.failures.single().code)
    }

    @Test
    fun globalSearchFailureIsReportedAndLaterQueriesStillRun() = runTest(dispatcher.scheduler) {
        val repository = EmptyRepository(matchFailuresRemaining = 1)
        val source = FakeSearchSource("catalog.a")
        val viewModel = viewModel(repository, source)

        viewModel.updateQuery("first")
        advanceSearch()
        assertEquals("catalog.search.exception", viewModel.state.value.globalFailure?.code)

        viewModel.updateQuery("second")
        advanceSearch()
        assertEquals("second", viewModel.state.value.stories.single().sources.single().title)
        assertEquals(null, viewModel.state.value.globalFailure)
    }

    @Test
    fun blankQueryDoesNotEraseFilterDiscoveryFailure() = runTest(dispatcher.scheduler) {
        val source = FakeSearchSource("catalog.a")
        val viewModel = viewModel(
            Registry(listOf(source), enabledFailuresRemaining = 1),
            EmptyRepository(),
        )
        runCurrent()
        assertEquals("catalog.search.filters_exception", viewModel.state.value.globalFailure?.code)

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        runCurrent()

        assertEquals("catalog.search.filters_exception", viewModel.state.value.globalFailure?.code)
    }

    @Test
    fun recentSearchesStayMemoryOnly() = runTest(dispatcher.scheduler) {
        val source = FakeSearchSource("catalog.a")
        val first = viewModel(source)
        first.updateQuery("novel")
        advanceSearch()
        assertEquals(listOf("novel"), first.state.value.recentQueries)

        val recreated = viewModel(source)
        runCurrent()
        assertTrue(recreated.state.value.recentQueries.isEmpty())
    }

    @Test
    fun selectingFreshResultNavigatesWithoutDetailsEnrichment() = runTest(dispatcher.scheduler) {
        val source = FakeSearchSource("catalog.a")
        val viewModel = viewModel(source)
        viewModel.updateQuery("novel")
        advanceSearch()
        val result = viewModel.state.value.stories.single()
        var selectedStoryId: StoryId? = null

        viewModel.selectStory(result) { selectedStoryId = it }
        runCurrent()

        assertEquals(result.story.id, selectedStoryId)
        assertEquals(0, source.detailsCalls)
    }


    @Test
    fun rangeSliderSnapsToConfiguredStep() {
        assertEquals(4.0, snapRangeValue(raw = 4.2, minimum = 0.0, maximum = 10.0, step = 2.0))
        assertEquals(10.0, snapRangeValue(raw = 11.0, minimum = 0.0, maximum = 10.0, step = 2.0))
    }

    private fun TestScope.viewModel(vararg sources: FakeSearchSource): SearchViewModel =
        viewModel(EmptyRepository(), *sources)

    private fun TestScope.viewModel(
        repository: EmptyRepository,
        vararg sources: FakeSearchSource,
    ): SearchViewModel = viewModel(Registry(sources.toList()), repository)

    private fun TestScope.viewModel(registry: Registry, repository: EmptyRepository): SearchViewModel {
        val matcher = StoryMatcher()
        val clock = Clock { 100L }
        val bootstrap = CanonicalBootstrapUseCase(repository.canonical) { storyId, _ ->
            val ready = repository.canonical.state(storyId) as? CanonicalStoryState.Ready
            if (ready == null) {
                CanonicalFusionResult.Failed(storyId, "test.canonical_missing", retryable = false)
            } else {
                CanonicalFusionResult.Unchanged(ready.generation)
            }
        }
        return SearchViewModel(
            CatalogSearchService(registry, repository, matcher, clock, bootstrap, CatalogFilterCache()),
        )
    }

    private suspend fun kotlinx.coroutines.test.TestScope.advanceSearch() {
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        runCurrent()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

private class Registry(
    private val sources: List<CatalogSource>,
    private var enabledFailuresRemaining: Int = 0,
) : CatalogSourceRegistry {
    override suspend fun enabled(): List<CatalogSource> {
        if (enabledFailuresRemaining > 0) {
            enabledFailuresRemaining--
            error("catalog registry unavailable")
        }
        return sources
    }
    override suspend fun source(pluginId: PluginId) = sources.firstOrNull { it.pluginId == pluginId }
}

private class FakeSearchSource(id: String) : CatalogSource {
    override val pluginId = PluginId(id)
    override val version = "1.0.0"
    var searchCalls = 0
    var detailsCalls = 0
    var lastRequest: SourceSearchRequest? = null
    var searchAction: suspend (SourceSearchRequest) -> CatalogSourceResult<SourceSearchPage> = { request ->
        successPage(request.query)
    }

    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> {
        searchCalls++
        lastRequest = request
        return searchAction(request)
    }

    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = CatalogSourceResult.Success(
        listOf(
            SourceOptionFilter(
                "genre",
                "Genre",
                multiple = true,
                options = listOf(SourceFilterOption("fantasy", "Fantasy")),
            ),
        ),
    )
    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> = error("unused")
    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> {
        detailsCalls++
        return CatalogSourceResult.Success(
            SourceDetails(
                sourceId = sourceId,
                sourceUrl = "https://example.test/$sourceId",
                title = lastRequest?.query.orEmpty(),
                aliases = emptySet(),
                authors = emptySet(),
                description = "Loaded details",
                genres = emptySet(),
                contentType = SourceContentType.WEB_NOVEL,
                languageTags = emptySet(),
                coverUrl = null,
                scoreValue = null,
                scoreScale = null,
                popularityRank = null,
            ),
        )
    }
}

private fun successPage(title: String): CatalogSourceResult<SourceSearchPage> = CatalogSourceResult.Success(
    SourceSearchPage(
        listOf(SourceItem("source-$title", title, SourceContentType.WEB_NOVEL, emptySet(), null, null, null)),
        null,
    ),
)

private class EmptyRepository(
    private var matchFailuresRemaining: Int = 0,
) : CatalogRepository {
    val canonical = SearchCanonicalRepository()
    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = flowOf(emptyList())
    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun matchSnapshot(): CatalogMatchSnapshot {
        if (matchFailuresRemaining > 0) {
            matchFailuresRemaining--
            error("catalog unavailable")
        }
        return CatalogMatchSnapshot(emptyList())
    }
    override suspend fun metadataSnapshot(
        key: app.openstory.catalog.metadata.CatalogMetadataKey,
    ): app.openstory.catalog.metadata.CatalogMetadataSnapshot? = null

    override suspend fun sourceRecord(key: CatalogMetadataKey): app.openstory.catalog.evidence.CatalogSourceRecord? = null

    override suspend fun sourceRecords(storyId: StoryId): List<app.openstory.catalog.evidence.CatalogSourceRecord> = emptyList()

    override suspend fun sourceRecords(): List<app.openstory.catalog.evidence.CatalogSourceRecord> = emptyList()

    override suspend fun commitHomeRefresh(
        mutation: CatalogHomeMutation,
    ): Outcome<Unit, CatalogStoreFailure> = Outcome.Success(Unit)
    override suspend fun commitSearchSummaries(
        mutation: CatalogSearchSummaryMutation,
    ): Outcome<CatalogSearchSummaryCommitResult, CatalogStoreFailure> {
        val mapping = mutation.entries.associate { entry ->
            val key = SourceKey(entry.pluginId, entry.sourceId)
            canonical.put(searchReadyState(entry))
            key to entry.storyId
        }
        return Outcome.Success(CatalogSearchSummaryCommitResult(mapping))
    }

    override suspend fun commitDetails(
        mutation: CatalogDetailsMutation,
    ): Outcome<StoryId, CatalogStoreFailure> = Outcome.Success(mutation.storyId)

}

private class SearchCanonicalRepository : CanonicalCatalogRepository {
    private val states = mutableMapOf<StoryId, CanonicalStoryState.Ready>()

    fun put(state: CanonicalStoryState.Ready) { states[state.story.id] = state }
    override fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?> = flowOf(states[storyId])
    override fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>> = flowOf(states.values.toList())
    override suspend fun state(storyId: StoryId): CanonicalStoryState? = states[storyId]
    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = emptyList()
    override suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration? = states[storyId]?.generation
    override suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference =
        requireNotNull(states[storyId]).preference
    override suspend fun setSourcePreference(preference: CanonicalSourcePreference) = Unit
    override suspend fun persistCandidate(candidate: CanonicalGeneration, expectedActiveGenerationId: String?) = false
    override suspend fun markHealth(storyId: StoryId, health: CanonicalHealth) = Unit
    override suspend fun cleanupObsoleteGenerations(storyId: StoryId) = Unit
}

private fun searchReadyState(entry: CatalogEntry): CanonicalStoryState.Ready {
    val key = SourceKey(entry.pluginId, entry.sourceId)
    val source = CanonicalSourceSummary(
        key, entry, CatalogMetadataStamp("1.0.0", 100L), null,
        "identity:${key.pluginId.value}:${key.sourceId}", "fusion:${key.pluginId.value}:${key.sourceId}",
    )
    val generation = CanonicalGeneration(
        id = "gen:${entry.storyId.value}",
        storyId = entry.storyId,
        fusionPolicyVersion = 1,
        primarySelectionPolicyVersion = 1,
        fusionFingerprint = "fusion:${entry.storyId.value}",
        effectivePrimary = key,
        metadata = CanonicalMetadata(
            entry.title, null, entry.coverUrl, null, null, emptyList(), entry.authors.toList(),
            emptyList(), emptyList(), null, null, null,
        ),
        health = CanonicalHealth.FRESH,
        provenance = emptyMap(),
        createdAtEpochMillis = 100L,
    )
    return CanonicalStoryState.Ready(
        Story(entry.storyId, entry.contentType),
        CanonicalHealth.FRESH,
        CanonicalSourcePreference(entry.storyId, CanonicalSourcePreferenceMode.AUTO, null, 0),
        listOf(source),
        generation,
    )
}
