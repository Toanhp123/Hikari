package app.openstory.home.domain

import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.database.repository.CatalogRepository
import app.openstory.matching.CatalogStoryResolver
import app.openstory.model.CanonicalStory
import app.openstory.model.PluginId
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
    private val sources: CatalogSourceRegistry,
    resolver: CatalogStoryResolver,
    private val candidates: CanonicalStoryCandidates,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private val canonicalizer = SearchCanonicalizer(resolver)

    constructor(
        sources: CatalogSourceRegistry,
        resolver: CatalogStoryResolver,
        repository: CatalogRepository,
    ) : this(
        sources = sources,
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
        val catalogSources = sources.enabled().sortedBy { source -> source.pluginId.value }
        val candidateResult = loadCandidates()
        val outcomes = catalogSources.map { source ->
            async {
                searchCatalog(source, request)
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
        source: CatalogSource,
        request: SearchRequest,
    ): CatalogSearchOutcome {
        val filterResult = callFilters(source)
        val searchResult = callSearch(source, request)
        return CatalogSearchOutcome(
            source = source,
            page = searchResult.value,
            filters = filterResult.value.orEmpty(),
            failure = searchResult.error ?: filterResult.error,
        )
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun callSearch(
        source: CatalogSource,
        request: SearchRequest,
    ): SafeCall<SourceSearchPage> = try {
        when (val result = source.search(
            SourceSearchRequest(
                query = request.query,
                filterValues = request.filterValues[source.pluginId].orEmpty(),
            ),
        )) {
            is CatalogSourceResult.Success -> SafeCall(value = result.value)
            is CatalogSourceResult.Failure -> SafeCall(error = result.failure.toAppError())
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        SafeCall(error = pluginFailure(SEARCH_FAILED_CODE))
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun callFilters(
        source: CatalogSource,
    ): SafeCall<List<SourceFilter>> = try {
        when (val result = source.filters()) {
            is CatalogSourceResult.Success -> SafeCall(value = result.value)
            is CatalogSourceResult.Failure -> SafeCall(error = result.failure.toAppError())
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
        val source: CatalogSource,
        val page: SourceSearchPage?,
        val filters: List<SourceFilter>,
        val failure: AppError?,
    ) {
        fun pageForCanonicalization(): SearchCatalogPage? = page?.let { value ->
            SearchCatalogPage(source = source, page = value)
        }

        fun filtersForUi(): SearchCatalogFilters = SearchCatalogFilters(
            pluginId = source.pluginId,
            pluginVersion = source.version,
            definitions = filters.mapNotNull { filter -> filter.toSearchFilterDefinition() },
        )

        fun failureEntry(): Pair<PluginId, AppError>? = failure?.let { error -> source.pluginId to error }
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
        const val SEARCH_FAILED_CODE = "catalog.search_failed"
        const val FILTERS_FAILED_CODE = "catalog.filters_failed"
        const val CANDIDATES_FAILED_CODE = "catalog.candidates_failed"
    }
}

private fun app.openstory.catalog.source.CatalogSourceFailure.toAppError() = AppError.Plugin(
    code = code,
    retryable = retryable,
)
