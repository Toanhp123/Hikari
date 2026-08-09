package app.openstory.home.domain

import app.openstory.catalog.search.CatalogSearchRequest
import app.openstory.catalog.search.CatalogSearchService
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceFilter
import app.openstory.common.AppError
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
    private val searchService: CatalogSearchService,
    private val sources: CatalogSourceRegistry,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private val canonicalizer = SearchCanonicalizer()

    init {
        require(debounceMillis >= 0L) { "Search debounce must not be negative" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun results(requests: Flow<SearchRequest>): Flow<SearchResultPage> = requests
        .distinctUntilChanged()
        .flatMapLatest { request ->
            val query = request.query.trim()
            if (query.isBlank()) flowOf(SearchResultPage(query = query))
            else searchFlow(request.copy(query = query))
        }

    private fun searchFlow(request: SearchRequest): Flow<SearchResultPage> = flow {
        delay(debounceMillis)
        emit(SearchResultPage(query = request.query, searching = true))
        emit(search(request))
    }

    private suspend fun search(request: SearchRequest): SearchResultPage = supervisorScope {
        val enabled = sources.enabled().sortedBy { it.pluginId.value }
        val result = searchService.search(
            CatalogSearchRequest(
                query = request.query,
                filterValues = request.filterValues.values.flatMap { it.entries }.associate { it.toPair() },
            ),
        )
        val filters = enabled.map { source -> async { loadFilters(source) } }.awaitAll()
        SearchResultPage(
            query = request.query,
            results = canonicalizer.canonicalize(result, enabled.associate { it.pluginId to it.version }),
            filters = filters.map { it.filters },
            failures = buildMap {
                result.failures.forEach { put(it.pluginId, AppError.Plugin(it.code, it.retryable)) }
                filters.forEach { loaded -> loaded.error?.let { put(loaded.filters.pluginId, it) } }
            },
        )
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun loadFilters(source: CatalogSource): LoadedFilters = try {
        when (val result = source.filters()) {
            is CatalogSourceResult.Success -> LoadedFilters(source.toFilters(result.value))
            is CatalogSourceResult.Failure -> LoadedFilters(
                source.toFilters(emptyList()),
                AppError.Plugin(result.failure.code, result.failure.retryable),
            )
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        LoadedFilters(source.toFilters(emptyList()), AppError.Plugin(FILTERS_FAILED_CODE, false))
    }

    private fun CatalogSource.toFilters(definitions: List<SourceFilter>) = SearchCatalogFilters(
        pluginId = pluginId,
        pluginVersion = version,
        definitions = definitions.mapNotNull { it.toSearchFilterDefinition() },
    )

    private data class LoadedFilters(
        val filters: SearchCatalogFilters,
        val error: AppError? = null,
    )

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
        const val FILTERS_FAILED_CODE = "catalog.filters_failed"
    }
}
