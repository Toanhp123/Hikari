package app.openstory.home.ui

import androidx.lifecycle.ViewModel
import app.openstory.home.domain.SearchCatalogs
import app.openstory.home.domain.SearchRequest
import app.openstory.home.domain.SearchResultPage
import app.openstory.common.id.PluginId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel internal constructor(
    private val searchCatalogs: SearchCatalogs,
    private val scope: CoroutineScope,
) : ViewModel() {
    constructor(searchCatalogs: SearchCatalogs) : this(
        searchCatalogs = searchCatalogs,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )
    private val request = MutableStateFlow(SearchRequest(query = ""))
    private val recentQueries = MutableStateFlow<List<String>>(emptyList())
    private val page = searchCatalogs.results(request).stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SearchResultPage(),
    )

    val state = combine(
        request,
        page,
        recentQueries,
    ) { currentRequest, currentPage, recent ->
        SearchScreenState(
            query = currentRequest.query,
            filterValues = currentRequest.filterValues,
            page = currentPage,
            recentQueries = recent,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SearchScreenState(),
    )

    init {
        scope.launch {
            page.collect { current ->
                if (!current.searching && current.query.isNotBlank()) {
                    rememberQuery(current.query)
                }
            }
        }
    }

    fun updateQuery(query: String) {
        request.value = request.value.copy(query = query)
    }

    fun selectRecent(query: String) {
        updateQuery(query)
    }

    fun setFilterValues(
        pluginId: PluginId,
        filterId: String,
        values: List<String>,
    ) {
        val current = request.value
        val currentCatalogValues = current.filterValues[pluginId].orEmpty()
        val nextCatalogValues = if (values.isEmpty()) {
            currentCatalogValues - filterId
        } else {
            currentCatalogValues + (filterId to values)
        }
        val nextValues = if (nextCatalogValues.isEmpty()) {
            current.filterValues - pluginId
        } else {
            current.filterValues + (pluginId to nextCatalogValues)
        }
        request.value = current.copy(filterValues = nextValues)
    }

    fun clearFilters(pluginId: PluginId) {
        request.value = request.value.copy(
            filterValues = request.value.filterValues - pluginId,
        )
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    private fun rememberQuery(query: String) {
        recentQueries.value = buildList {
            add(query)
            addAll(recentQueries.value.filterNot { recent -> recent == query })
        }.take(MAX_RECENT_QUERIES)
    }

    private companion object {
        const val MAX_RECENT_QUERIES = 8
    }
}

data class SearchScreenState(
    val query: String = "",
    val filterValues: Map<PluginId, Map<String, List<String>>> = emptyMap(),
    val page: SearchResultPage = SearchResultPage(),
    val recentQueries: List<String> = emptyList(),
)
