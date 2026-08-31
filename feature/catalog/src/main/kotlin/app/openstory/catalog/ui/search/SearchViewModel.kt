package app.openstory.catalog.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.search.CatalogSearchRequest
import app.openstory.catalog.search.CatalogSearchResult
import app.openstory.catalog.search.CatalogSearchSelectionResult
import app.openstory.catalog.search.CatalogSearchService
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel private constructor(
    private val searchService: CatalogSearchService,
    private val storySelector: SearchStorySelector,
) : ViewModel() {
    @Inject
    constructor(searchService: CatalogSearchService) : this(
        searchService = searchService,
        storySelector = SearchStorySelector(searchService::select),
    )

    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private var attemptSequence = 0L
    private var selectionAttemptSequence = 0L
    private var filterLoadJob: Job? = null
    private val searchAttempt = MutableStateFlow(
        SearchAttempt(
            request = CatalogSearchRequest(query = ""),
            sequence = attemptSequence,
        ),
    )

    init {
        loadFilters()
        viewModelScope.launch {
            searchAttempt
                .mapLatest { attempt ->
                    if (!attempt.request.isSearchable()) {
                        null
                    } else {
                        delay(SEARCH_DEBOUNCE_MILLIS)
                        execute(attempt)
                    }
                }
                .collect { execution ->
                    execution?.let(::publish)
                }
        }
    }

    fun updateQuery(value: String) {
        val current = mutableState.value
        val nextRequest = request(value, current.filterValues)
        if (searchAttempt.value.request == nextRequest) {
            mutableState.update { it.copy(query = value) }
        } else {
            startAttempt(nextRequest) { it.copy(query = value) }
        }
    }

    fun selectRecent(value: String) {
        updateQuery(value)
    }

    fun setFilterValues(pluginId: PluginId, filterId: String, values: List<String>) {
        val current = mutableState.value.filterValues
        val sourceValues = current[pluginId].orEmpty()
        val nextSourceValues = if (values.isEmpty()) {
            sourceValues - filterId
        } else {
            sourceValues + (filterId to values.toList())
        }
        val nextFilters = if (nextSourceValues.isEmpty()) {
            current - pluginId
        } else {
            current + (pluginId to nextSourceValues)
        }
        val nextRequest = request(mutableState.value.query, nextFilters)
        if (searchAttempt.value.request == nextRequest) {
            mutableState.update { it.copy(filterValues = nextFilters) }
        } else {
            startAttempt(nextRequest) { it.copy(filterValues = nextFilters) }
        }
    }

    fun clearFilters(pluginId: PluginId) {
        val nextFilters = mutableState.value.filterValues - pluginId
        val nextRequest = request(mutableState.value.query, nextFilters)
        if (searchAttempt.value.request == nextRequest) {
            mutableState.update { it.copy(filterValues = nextFilters) }
        } else {
            startAttempt(nextRequest) { it.copy(filterValues = nextFilters) }
        }
    }

    fun retrySearch() {
        val request = searchAttempt.value.request
        if (!request.isSearchable()) return
        startAttempt(request)
    }

    fun retryFilters() {
        loadFilters()
    }

    fun selectStory(story: CatalogSearchStory, onSelected: (StoryId) -> Unit) {
        val attempt = ++selectionAttemptSequence
        mutableState.update { it.copy(selectionIssue = null) }
        viewModelScope.launch {
            when (val result = storySelector.select(story)) {
                is CatalogSearchSelectionResult.Success -> {
                    if (selectionAttemptSequence != attempt) return@launch
                    onSelected(result.storyId)
                }
                is CatalogSearchSelectionResult.Failure -> {
                    if (selectionAttemptSequence != attempt) return@launch
                    mutableState.update {
                        it.copy(selectionIssue = CatalogUiFailure(result.code, result.retryable))
                    }
                }
            }
        }
    }

    private fun loadFilters() {
        filterLoadJob?.cancel()
        filterLoadJob = viewModelScope.launch {
            try {
                val filters = searchService.filters()
                mutableState.update {
                    it.copy(
                        filterGroups = filters,
                        filterIssue = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        filterIssue = CatalogUiFailure(FILTERS_EXCEPTION_CODE, retryable = true),
                    )
                }
            }
        }
    }

    private fun startAttempt(
        request: CatalogSearchRequest,
        updateState: (SearchUiState) -> SearchUiState = { it },
    ) {
        selectionAttemptSequence += 1L
        val next = SearchAttempt(
            request = request,
            sequence = ++attemptSequence,
        )
        searchAttempt.value = next
        mutableState.update { current ->
            updateState(current).copy(
                resultState = if (request.isSearchable()) {
                    SearchResultState.Active(ContentState.Pending)
                } else {
                    SearchResultState.Idle
                },
                selectionIssue = null,
            )
        }
    }

    private suspend fun execute(attempt: SearchAttempt): SearchExecution = try {
        SearchExecution(
            attempt = attempt,
            content = ContentState.Ready(searchService.search(attempt.request)),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        SearchExecution(
            attempt = attempt,
            content = ContentState.Failed(
                CatalogUiFailure(SEARCH_EXCEPTION_CODE, retryable = true),
            ),
        )
    }

    private fun publish(execution: SearchExecution) {
        if (searchAttempt.value != execution.attempt) return

        val recent = buildList {
            add(execution.attempt.request.query)
            addAll(
                mutableState.value.recentQueries.filterNot {
                    it == execution.attempt.request.query
                },
            )
        }.take(MAX_RECENT_QUERIES)

        mutableState.update {
            it.copy(
                resultState = SearchResultState.Active(execution.content),
                recentQueries = recent,
            )
        }
    }

    private fun request(
        rawQuery: String,
        filters: Map<PluginId, Map<String, List<String>>>,
    ): CatalogSearchRequest = CatalogSearchRequest(
        query = rawQuery.trim(),
        filterValues = filters,
    )

    private data class SearchAttempt(
        val request: CatalogSearchRequest,
        val sequence: Long,
    )

    private data class SearchExecution(
        val attempt: SearchAttempt,
        val content: ContentState<CatalogSearchResult>,
    )

    companion object {
        internal fun createForTest(
            searchService: CatalogSearchService,
            storySelector: SearchStorySelector,
        ): SearchViewModel = SearchViewModel(searchService, storySelector)

        private const val MINIMUM_QUERY_LENGTH = 2
        private const val SEARCH_DEBOUNCE_MILLIS = 300L
        private const val MAX_RECENT_QUERIES = 8
        private const val FILTERS_EXCEPTION_CODE = "catalog.search.filters_exception"
        private const val SEARCH_EXCEPTION_CODE = "catalog.search.exception"
    }

    private fun CatalogSearchRequest.isSearchable(): Boolean = query.length >= MINIMUM_QUERY_LENGTH
}

internal fun interface SearchStorySelector {
    suspend fun select(story: CatalogSearchStory): CatalogSearchSelectionResult
}
