package app.openstory.catalog.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    repository: CatalogRepository,
    refreshPipeline: DiscoverRefreshPipeline,
    projection: DiscoverProjectionPipeline,
) : ViewModel() {
    private val observationFailure = MutableStateFlow<DiscoverUiFailure?>(null)
    private val refreshFailure = MutableStateFlow<DiscoverUiFailure?>(null)
    private val projection = projection
    private val homes = repository.observeHomes()
        .preserveLatestOnFailure(HOME_OBSERVE_EXCEPTION_CODE, emptyList())
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            replay = 1,
        )
    private val selectedContentType = MutableStateFlow(ContentType.MANGA)
    private val dependencies = DiscoverDependencies(
        homes = homes,
        content = combine(homes, selectedContentType) { currentHomes, contentType ->
            projection.project(currentHomes, contentType)
        }.preserveLatestOnFailure(
            RANKING_OBSERVE_EXCEPTION_CODE,
            DiscoverSemanticContent.empty(selectedContentType.value),
        ),
        refresh = refreshPipeline::refresh,
    )
    private val initialLoading = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
    private val refreshReport = MutableStateFlow<DiscoverRefreshReport?>(null)
    private var bootstrapAttempted = false

    init {
        bootstrapEmptyCache()
    }

    private val contentState = combine(
        dependencies.content,
        initialLoading,
        refreshing,
        refreshReport,
    ) { content, loading, busy, report ->
        content.toUiState(
            loading = loading,
            refreshing = busy,
            refreshReport = report,
        )
    }

    val state = combine(contentState, observationFailure, refreshFailure) { content, observation, refresh ->
        content.copy(
            observationFailure = observation,
            refreshFailure = refresh,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DiscoverUiState(),
    )

    private fun bootstrapEmptyCache() {
        viewModelScope.launch {
            if (bootstrapAttempted) return@launch
            bootstrapAttempted = true
            val cachedHomes = dependencies.homes.first()
            if (cachedHomes.isEmpty() && observationFailure.value == null) {
                performRefresh()
            }
            initialLoading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch { performRefresh() }
    }

    private suspend fun performRefresh() {
        if (refreshing.value) return
        refreshing.value = true
        try {
            val cachedHomes = dependencies.homes.first()
            refreshReport.value = dependencies.refresh(cachedHomes)
            refreshFailure.value = null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            refreshFailure.value = DiscoverUiFailure(REFRESH_EXCEPTION_CODE, retryable = true)
        } finally {
            refreshing.value = false
        }
    }

    fun selectContentType(contentType: ContentType) {
        if (contentType != ContentType.MANGA) return
        selectedContentType.value = contentType
    }

    private data class DiscoverDependencies(
        val homes: Flow<List<CatalogHomeSnapshot>>,
        val content: Flow<DiscoverSemanticContent>,
        val refresh: suspend (List<CatalogHomeSnapshot>) -> DiscoverRefreshReport,
    )

    private fun <T> Flow<T>.preserveLatestOnFailure(code: String, initial: T): Flow<T> = flow {
        var latest = initial
        try {
            this@preserveLatestOnFailure.collect { value ->
                latest = value
                emit(value)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            observationFailure.value = DiscoverUiFailure(code, retryable = true)
            emit(latest)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val HOME_OBSERVE_EXCEPTION_CODE = "catalog.home.observe_exception"
        const val RANKING_OBSERVE_EXCEPTION_CODE = "catalog.home.ranking_exception"
        const val REFRESH_EXCEPTION_CODE = "catalog.home.refresh_exception"
    }
}
