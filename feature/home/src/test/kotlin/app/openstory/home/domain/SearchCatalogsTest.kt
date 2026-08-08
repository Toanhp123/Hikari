package app.openstory.home.domain

import app.openstory.common.AppResult
import app.openstory.matching.CatalogStoryResolver
import app.openstory.matching.defaultCatalogMatchPolicy
import app.openstory.model.ContentType
import app.openstory.model.PluginId
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogFilterOption
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogScore
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.catalog.CatalogSection
import app.openstory.plugin.api.catalog.CatalogSelectFilter
import app.openstory.plugin.api.content.ContentPlugin
import app.openstory.plugin.host.HostedPlugin
import app.openstory.plugin.host.PluginHost
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
            host = host,
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
                    AppResult.Success(
                        Page(
                            items = listOf(searchCard("${request.query}-result", request.query)),
                            nextToken = null,
                        ),
                    )
                },
            ),
        )
        val requests = MutableStateFlow(SearchRequest(query = ""))
        val useCase = SearchCatalogs(
            host = host,
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
                    AppResult.Success(
                        Page(
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
            host = host,
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
                    AppResult.Success(Page(listOf(searchCard("b-1", "Reborn", 90.0, 100.0)), null))
                },
                searchPlugin("catalog.a") {
                    AppResult.Success(Page(listOf(searchCard("a-1", "Reborn", 8.0, 10.0)), null))
                },
            ),
        )
        val page = SearchCatalogs(
            host = host,
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
                CatalogSelectFilter(
                    id = "genre",
                    label = "Genre",
                    options = listOf(CatalogFilterOption("fantasy", "Fantasy")),
                ),
            ),
        ) { AppResult.Success(Page(emptyList(), null)) }
        val pluginB = searchPlugin("catalog.b") { AppResult.Success(Page(emptyList(), null)) }
        val host = FakeSearchHost(listOf(pluginA, pluginB))
        val request = SearchRequest(
            query = "novel",
            filterValues = mapOf(
                PluginId("catalog.a") to mapOf("genre" to listOf("fantasy")),
            ),
        )

        val page = SearchCatalogs(
            host = host,
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
) : PluginHost {
    var enabledCatalogCalls: Int = 0
        private set

    override suspend fun catalog(id: PluginId): HostedPlugin<CatalogPlugin> =
        catalogs.single { it.hosted.id == id }.hosted

    override suspend fun content(id: PluginId): HostedPlugin<ContentPlugin> = error("Not used")

    override suspend fun enabledCatalogs(): List<HostedPlugin<CatalogPlugin>> {
        enabledCatalogCalls += 1
        return catalogs.map(RecordingCatalogPlugin::hosted)
    }

    override suspend fun enabledContentSources(): List<HostedPlugin<ContentPlugin>> = emptyList()
}

private class RecordingCatalogPlugin(
    id: String,
    filters: List<CatalogFilterDefinition>,
    private val searchAction: suspend (CatalogSearchRequest) -> AppResult<Page<CatalogCard>>,
) {
    val requests = mutableListOf<CatalogSearchRequest>()

    val hosted: HostedPlugin<CatalogPlugin> = HostedPlugin(
        id = PluginId(id),
        version = "1.0.0",
        instance = object : CatalogPlugin {
            override suspend fun home(request: CatalogHomeRequest): AppResult<List<CatalogSection>> =
                AppResult.Success(emptyList())

            override suspend fun search(request: CatalogSearchRequest): AppResult<Page<CatalogCard>> {
                requests += request
                return searchAction(request)
            }

            override suspend fun details(sourceId: String): AppResult<CatalogDetails> = error("Not used")

            override suspend fun filters(): AppResult<List<CatalogFilterDefinition>> = AppResult.Success(filters)
        },
    )
}

private fun searchPlugin(
    id: String,
    filters: List<CatalogFilterDefinition> = emptyList(),
    search: suspend (CatalogSearchRequest) -> AppResult<Page<CatalogCard>>,
): RecordingCatalogPlugin = RecordingCatalogPlugin(id, filters, search)

private fun searchCard(
    sourceId: String,
    title: String,
    score: Double = 8.0,
    scale: Double = 10.0,
): CatalogCard = CatalogCard(
    sourceId = sourceId,
    title = title,
    contentType = ContentType.WEB_NOVEL,
    authors = listOf("Author"),
    image = null,
    score = CatalogScore(score, scale),
)
