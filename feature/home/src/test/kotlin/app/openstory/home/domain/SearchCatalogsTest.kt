package app.openstory.home.domain

import app.openstory.catalog.source.CatalogSource
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
import app.openstory.matching.CatalogStoryResolver
import app.openstory.matching.defaultCatalogMatchPolicy
import app.openstory.model.PluginId
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchCatalogsTest {
    @Test
    fun blankQueryDoesNotInvokePlugins() = runTest {
        val host = FakeSearchHost(emptyList())
        val requests = MutableStateFlow(SearchRequest(query = "   "))
        val result = SearchCatalogs(
            sources = host,
            resolver = CatalogStoryResolver(defaultCatalogMatchPolicy()),
            candidates = CanonicalStoryCandidates { emptyList() },
            debounceMillis = 0L,
        ).results(requests).first()

        assertTrue(result.results.isEmpty())
        assertEquals(0, host.enabledCatalogCalls)
    }

    @Test
    fun lateOldQueryCannotReplaceNewQuery() = runTest {
        val oldStarted = CompletableDeferred<Unit>()
        val host = FakeSearchHost(
            listOf(
                searchPlugin("catalog.a") { request ->
                    if (request.query == "old") {
                        oldStarted.complete(Unit)
                        delay(1_000)
                    } else {
                        delay(10)
                    }
                    CatalogSourceResult.Success(
                        SourceSearchPage(
                            items = listOf(searchCard("${request.query}-result", request.query)),
                            nextToken = null,
                        ),
                    )
                },
            ),
        )
        val requests = MutableStateFlow(SearchRequest(query = ""))
        val useCase = SearchCatalogs(
            sources = host,
            resolver = CatalogStoryResolver(defaultCatalogMatchPolicy()),
            candidates = CanonicalStoryCandidates { emptyList() },
            debounceMillis = 300L,
        )
        val newest = async {
            useCase.results(requests).first { page ->
                page.query == "new" && !page.searching && page.results.isNotEmpty()
            }
        }

        requests.value = SearchRequest("old")
        advanceTimeBy(300)
        runCurrent()
        oldStarted.await()

        requests.value = SearchRequest("new")
        advanceTimeBy(300)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()

        val page = newest.await()
        assertEquals("new", page.query)
        assertEquals(listOf("new"), page.results.map { it.title })
    }

    @Test
    fun changingQueryCancelsInFlightSearchBeforeNewDebounceExpires() = runTest {
        val oldStarted = CompletableDeferred<Unit>()
        val host = FakeSearchHost(
            listOf(
                searchPlugin("catalog.a") { request ->
                    if (request.query == "old") {
                        oldStarted.complete(Unit)
                        delay(100)
                    }
                    CatalogSourceResult.Success(
                        SourceSearchPage(
                            items = listOf(searchCard("${request.query}-result", request.query)),
                            nextToken = null,
                        ),
                    )
                },
            ),
        )
        val requests = MutableStateFlow(SearchRequest(query = ""))
        val pages = mutableListOf<SearchResultPage>()
        val useCase = SearchCatalogs(
            sources = host,
            resolver = CatalogStoryResolver(defaultCatalogMatchPolicy()),
            candidates = CanonicalStoryCandidates { emptyList() },
            debounceMillis = 300L,
        )
        backgroundScope.launch {
            useCase.results(requests).collect(pages::add)
        }

        requests.value = SearchRequest("old")
        advanceTimeBy(300)
        runCurrent()
        oldStarted.await()

        requests.value = SearchRequest("new")
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        assertTrue(
            pages.none { page -> page.query == "old" && !page.searching },
            "An in-flight old search must be cancelled as soon as the query changes.",
        )
    }

    @Test
    fun matchingCombinesDuplicateResultsButPreservesSourceScores() = runTest {
        val host = FakeSearchHost(
            listOf(
                searchPlugin("catalog.b") {
                    CatalogSourceResult.Success(
                        SourceSearchPage(listOf(searchCard("b-1", "Reborn", 90.0, 100.0)), null),
                    )
                },
                searchPlugin("catalog.a") {
                    CatalogSourceResult.Success(SourceSearchPage(listOf(searchCard("a-1", "Reborn", 8.0, 10.0)), null))
                },
            ),
        )
        val page = SearchCatalogs(
            sources = host,
            resolver = CatalogStoryResolver(defaultCatalogMatchPolicy()),
            candidates = CanonicalStoryCandidates { emptyList() },
            debounceMillis = 0L,
        ).results(MutableStateFlow(SearchRequest("reborn"))).first { !it.searching }

        assertEquals(1, page.results.size)
        assertEquals("Reborn", page.results.single().title)
        assertEquals(
            setOf("catalog.a" to 10.0, "catalog.b" to 100.0),
            page.results.single().sources.map { it.pluginId.value to it.scoreScale }.toSet(),
        )
    }

    @Test
    fun filterValuesStayScopedToOwningCatalog() = runTest {
        val pluginA = searchPlugin(
            id = "catalog.a",
            filters = listOf(
                SourceOptionFilter(
                    id = "genre",
                    label = "Genre",
                    multiple = false,
                    options = listOf(SourceFilterOption("fantasy", "Fantasy")),
                ),
            ),
        ) { CatalogSourceResult.Success(SourceSearchPage(emptyList(), null)) }
        val pluginB = searchPlugin("catalog.b") { CatalogSourceResult.Success(SourceSearchPage(emptyList(), null)) }
        val host = FakeSearchHost(listOf(pluginA, pluginB))
        val request = SearchRequest(
            query = "novel",
            filterValues = mapOf(
                PluginId("catalog.a") to mapOf("genre" to listOf("fantasy")),
            ),
        )

        val page = SearchCatalogs(
            sources = host,
            resolver = CatalogStoryResolver(defaultCatalogMatchPolicy()),
            candidates = CanonicalStoryCandidates { emptyList() },
            debounceMillis = 0L,
        ).results(MutableStateFlow(request)).first { !it.searching }

        assertEquals(mapOf("genre" to listOf("fantasy")), pluginA.requests.single().filterValues)
        assertTrue(pluginB.requests.single().filterValues.isEmpty())
        assertEquals("genre", page.filters.single { it.pluginId == PluginId("catalog.a") }.definitions.single().id)
    }
}

private class FakeSearchHost(
    private val catalogs: List<RecordingCatalogPlugin>,
) : CatalogSourceRegistry {
    var enabledCatalogCalls: Int = 0
        private set

    override suspend fun source(pluginId: PluginId): CatalogSource? = catalogs.singleOrNull { it.pluginId == pluginId }

    override suspend fun enabled(): List<CatalogSource> {
        enabledCatalogCalls += 1
        return catalogs
    }
}

private class RecordingCatalogPlugin(
    id: String,
    private val sourceFilters: List<SourceFilter>,
    private val searchAction: suspend (SourceSearchRequest) -> CatalogSourceResult<SourceSearchPage>,
) : CatalogSource {
    override val pluginId = PluginId(id)
    override val version = "1.0.0"
    val requests = mutableListOf<SourceSearchRequest>()

    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> =
        CatalogSourceResult.Success(emptyList())

    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> {
        requests += request
        return searchAction(request)
    }

    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> = error("Not used")
    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = CatalogSourceResult.Success(sourceFilters)
}

private fun searchPlugin(
    id: String,
    filters: List<SourceFilter> = emptyList(),
    search: suspend (SourceSearchRequest) -> CatalogSourceResult<SourceSearchPage>,
): RecordingCatalogPlugin = RecordingCatalogPlugin(id, filters, search)

private fun searchCard(
    sourceId: String,
    title: String,
    score: Double = 8.0,
    scale: Double = 10.0,
): SourceItem = SourceItem(
    sourceId = sourceId,
    title = title,
    contentType = SourceContentType.WEB_NOVEL,
    authors = setOf("Author"),
    coverUrl = null,
    scoreValue = score,
    scoreScale = scale,
)
