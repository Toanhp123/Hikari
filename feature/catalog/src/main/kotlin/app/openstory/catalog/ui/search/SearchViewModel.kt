package app.openstory.catalog.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.search.CatalogSearchRequest
import app.openstory.catalog.search.CatalogSearchResult
import app.openstory.catalog.search.CatalogSearchSelectionResult
import app.openstory.catalog.search.CatalogSearchService
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel @Inject constructor(
    private val searchService: CatalogSearchService,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filterValues = MutableStateFlow<Map<PluginId, Map<String, List<String>>>>(emptyMap())
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableState.value = try {
                mutableState.value.copy(
                    filterGroups = searchService.filters(),
                    filterFailure = null,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableState.value.copy(
                    filterFailure = SearchUiFailure(FILTERS_EXCEPTION_CODE, retryable = true),
                )
            }
        }
        viewModelScope.launch {
            combine(query, filterValues) { queryValue, filters ->
                CatalogSearchRequest(queryValue.trim(), filters)
            }
                .mapLatest { request ->
                    delay(SEARCH_DEBOUNCE_MILLIS)
                    execute(request)
                }
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

    fun selectStory(story: CatalogSearchStory, onSelected: (StoryId) -> Unit) {
        viewModelScope.launch {
            when (val result = searchService.select(story)) {
                is CatalogSearchSelectionResult.Success -> onSelected(result.storyId)
                is CatalogSearchSelectionResult.Failure -> {
                    mutableState.value = mutableState.value.copy(
                        operationFailure = SearchUiFailure(result.code, result.retryable),
                    )
                }
            }
        }
    }

    private suspend fun execute(request: CatalogSearchRequest): SearchExecution {
        if (request.query.length < MINIMUM_QUERY_LENGTH) {
            return SearchExecution(request.query, CatalogSearchResult(emptyList(), emptyList()), null)
        }
        mutableState.value = mutableState.value.copy(searching = true)
        return try {
            SearchExecution(request.query, searchService.search(request), null)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SearchExecution(
                request.query,
                CatalogSearchResult(emptyList(), emptyList()),
                SearchUiFailure(SEARCH_EXCEPTION_CODE, retryable = true),
            )
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
            operationFailure = execution.operationFailure,
            searching = false,
            recentQueries = recent,
        )
    }

    private data class SearchExecution(
        val query: String,
        val result: CatalogSearchResult,
        val operationFailure: SearchUiFailure?,
    )

    private companion object {
        const val MINIMUM_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val MAX_RECENT_QUERIES = 8
        const val FILTERS_EXCEPTION_CODE = "catalog.search.filters_exception"
        const val SEARCH_EXCEPTION_CODE = "catalog.search.exception"
    }
}
