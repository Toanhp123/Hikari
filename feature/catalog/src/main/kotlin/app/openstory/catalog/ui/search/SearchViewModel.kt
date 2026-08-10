package app.openstory.catalog.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.search.CatalogSearchRequest
import app.openstory.catalog.search.CatalogSearchResult
import app.openstory.catalog.search.CatalogSearchService
import app.openstory.common.id.PluginId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel @Inject constructor(
    private val searchService: CatalogSearchService,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filterValues = MutableStateFlow<Map<PluginId, Map<String, List<String>>>>(emptyMap())
    private val mutableState = MutableStateFlow(SearchUiState())
    val state = mutableState

    init {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(filterGroups = searchService.filters())
        }
        viewModelScope.launch {
            combine(query, filterValues) { queryValue, filters ->
                CatalogSearchRequest(queryValue.trim(), filters)
            }
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .mapLatest(::execute)
                .collect(::publish)
        }
    }

    fun updateQuery(value: String) {
        query.value = value
        mutableState.value = mutableState.value.copy(query = value)
    }

    fun selectRecent(value: String) {
        updateQuery(value)
    }

    fun setFilterValues(pluginId: PluginId, filterId: String, values: List<String>) {
        val current = filterValues.value
        val sourceValues = current[pluginId].orEmpty()
        val nextSourceValues = if (values.isEmpty()) {
            sourceValues - filterId
        } else {
            sourceValues + (filterId to values)
        }
        filterValues.value = if (nextSourceValues.isEmpty()) {
            current - pluginId
        } else {
            current + (pluginId to nextSourceValues)
        }
        mutableState.value = mutableState.value.copy(filterValues = filterValues.value)
    }

    fun clearFilters(pluginId: PluginId) {
        filterValues.value = filterValues.value - pluginId
        mutableState.value = mutableState.value.copy(filterValues = filterValues.value)
    }

    private suspend fun execute(request: CatalogSearchRequest): SearchExecution {
        if (request.query.length < MINIMUM_QUERY_LENGTH) {
            return SearchExecution(request.query, CatalogSearchResult(emptyList(), emptyList()))
        }
        mutableState.value = mutableState.value.copy(searching = true)
        return try {
            SearchExecution(request.query, searchService.search(request))
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }

    private fun publish(execution: SearchExecution) {
        val recent = if (execution.query.length >= MINIMUM_QUERY_LENGTH) {
            buildList {
                add(execution.query)
                addAll(mutableState.value.recentQueries.filterNot { it == execution.query })
            }.take(MAX_RECENT_QUERIES)
        } else {
            mutableState.value.recentQueries
        }
        mutableState.value = mutableState.value.copy(
            query = query.value,
            stories = execution.result.stories,
            failures = execution.result.failures,
            searching = false,
            recentQueries = recent,
        )
    }

    private data class SearchExecution(
        val query: String,
        val result: CatalogSearchResult,
    )

    private companion object {
        const val MINIMUM_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val MAX_RECENT_QUERIES = 8
    }
}
