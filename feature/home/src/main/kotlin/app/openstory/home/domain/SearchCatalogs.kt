package app.openstory.home.domain

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.database.repository.CatalogRepository
import app.openstory.matching.CatalogStoryResolver
import app.openstory.model.CanonicalStory
import app.openstory.model.PluginId
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.host.HostedPlugin
import app.openstory.plugin.host.PluginHost
import java.util.concurrent.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.supervisorScope

class SearchCatalogs(
    private val host: PluginHost,
    resolver: CatalogStoryResolver,
    private val candidates: CanonicalStoryCandidates,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private val canonicalizer = SearchCanonicalizer(resolver)

    constructor(
        host: PluginHost,
        resolver: CatalogStoryResolver,
        repository: CatalogRepository,
    ) : this(
        host = host,
        resolver = resolver,
        candidates = CachedHomeCanonicalCandidates(repository),
    )

    init {
        require(debounceMillis >= 0L) {
            "Search debounce must not be negative"
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun results(requests: Flow<SearchRequest>): Flow<SearchResultPage> = requests
        .distinctUntilChanged()
        .flatMapLatest { request ->
            val query = request.query.trim()
            if (query.isBlank()) {
                flowOf(SearchResultPage(query = query))
            } else {
                searchFlow(request.copy(query = query))
            }
        }

    private fun searchFlow(request: SearchRequest): Flow<SearchResultPage> = flow {
        delay(debounceMillis)
        emit(SearchResultPage(query = request.query, searching = true))
        emit(search(request))
    }

    private suspend fun search(request: SearchRequest): SearchResultPage = supervisorScope {
        val hostedCatalogs = host.enabledCatalogs().sortedBy { hosted -> hosted.id.value }
        val candidateResult = loadCandidates()
        val outcomes = hostedCatalogs.map { hosted ->
            async {
                searchCatalog(hosted, request)
            }
        }.awaitAll()

        SearchResultPage(
            query = request.query,
            results = canonicalizer.canonicalize(
                pages = outcomes.mapNotNull(CatalogSearchOutcome::pageForCanonicalization),
                initialCandidates = candidateResult.candidates,
            ),
            filters = outcomes.map(CatalogSearchOutcome::filtersForUi),
            failures = outcomes.mapNotNull(CatalogSearchOutcome::failureEntry).toMap(),
            error = candidateResult.error,
        )
    }

    private suspend fun searchCatalog(
        hosted: HostedPlugin<CatalogPlugin>,
        request: SearchRequest,
    ): CatalogSearchOutcome {
        val filterResult = callFilters(hosted)
        val searchResult = callSearch(hosted, request)
        return CatalogSearchOutcome(
            hosted = hosted,
            page = searchResult.value,
            filters = filterResult.value.orEmpty(),
            failure = searchResult.error ?: filterResult.error,
        )
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun callSearch(
        hosted: HostedPlugin<CatalogPlugin>,
        request: SearchRequest,
    ): SafeCall<Page<CatalogCard>> = try {
        when (
            val result = hosted.instance.search(
                CatalogSearchRequest(
                    query = request.query,
                    filterValues = request.filterValues[hosted.id].orEmpty(),
                ),
            )
        ) {
            is AppResult.Success -> SafeCall(value = result.value)
            is AppResult.Failure -> SafeCall(error = result.error)
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        SafeCall(error = pluginFailure(SEARCH_FAILED_CODE))
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun callFilters(
        hosted: HostedPlugin<CatalogPlugin>,
    ): SafeCall<List<CatalogFilterDefinition>> = try {
        when (val result = hosted.instance.filters()) {
            is AppResult.Success -> SafeCall(value = result.value)
            is AppResult.Failure -> SafeCall(error = result.error)
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        SafeCall(error = pluginFailure(FILTERS_FAILED_CODE))
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun loadCandidates(): CandidateLoad = try {
        CandidateLoad(candidates = candidates.load().sortedBy { candidate -> candidate.id.value })
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        CandidateLoad(
            candidates = emptyList(),
            error = AppError.Storage(
                code = CANDIDATES_FAILED_CODE,
                retryable = false,
            ),
        )
    }

    private fun pluginFailure(code: String): AppError.Plugin = AppError.Plugin(
        code = code,
        retryable = false,
    )

    private data class SafeCall<T>(
        val value: T? = null,
        val error: AppError? = null,
    )

    private data class CandidateLoad(
        val candidates: List<CanonicalStory>,
        val error: AppError? = null,
    )

    private data class CatalogSearchOutcome(
        val hosted: HostedPlugin<CatalogPlugin>,
        val page: Page<CatalogCard>?,
        val filters: List<CatalogFilterDefinition>,
        val failure: AppError?,
    ) {
        fun pageForCanonicalization(): SearchCatalogPage? = page?.let { value ->
            SearchCatalogPage(hosted = hosted, page = value)
        }

        fun filtersForUi(): SearchCatalogFilters = SearchCatalogFilters(
            pluginId = hosted.id,
            pluginVersion = hosted.version,
            definitions = filters.map { filter -> filter.toSearchFilterDefinition() },
        )

        fun failureEntry(): Pair<PluginId, AppError>? = failure?.let { error -> hosted.id to error }
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
        const val SEARCH_FAILED_CODE = "catalog.search_failed"
        const val FILTERS_FAILED_CODE = "catalog.filters_failed"
        const val CANDIDATES_FAILED_CODE = "catalog.candidates_failed"
    }
}
