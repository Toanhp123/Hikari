package app.openstory.catalog.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.home.CatalogHomeQuery
import app.openstory.catalog.home.CatalogRefreshResult
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.ranking.RankedCatalogStory
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.id.PluginId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    repository: CatalogRepository,
    query: CatalogHomeQuery,
    refreshService: CatalogRefreshService,
) : ViewModel() {
    private val observationFailure = MutableStateFlow<DiscoverUiFailure?>(null)
    private val refreshFailure = MutableStateFlow<DiscoverUiFailure?>(null)
    private val dependencies = DiscoverDependencies(
        homes = repository.observeHomes().preserveLatestOnFailure(HOME_OBSERVE_EXCEPTION_CODE, emptyList()),
        rankedStories = query.rankedStories.preserveLatestOnFailure(RANKING_OBSERVE_EXCEPTION_CODE, emptyList()),
        refresh = {
            val results = refreshService.refresh()
            results.toReport(repository.observeHomes().first())
        },
    )
    private val selectedCatalogId = MutableStateFlow<PluginId?>(null)
    private val selectedSourceId = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val refreshReport = MutableStateFlow<DiscoverRefreshReport?>(null)

    private val selectionState = combine(
        selectedCatalogId,
        selectedSourceId,
    ) { pluginId, sourceId ->
        DiscoverSelection(pluginId, sourceId)
    }

    private val contentState = combine(
        dependencies.homes,
        dependencies.rankedStories,
        selectionState,
        refreshing,
        refreshReport,
    ) { homes, ranked, selection, busy, report ->
        projectDiscoverState(
            catalogs = homes,
            rankedStories = ranked,
            selectedCatalogId = selection.pluginId,
            selectedSourceId = selection.sourceId,
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

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                refreshReport.value = dependencies.refresh()
                refreshFailure.value = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                refreshFailure.value = DiscoverUiFailure(REFRESH_EXCEPTION_CODE, retryable = true)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun selectCatalog(pluginId: PluginId) {
        selectedCatalogId.value = pluginId
        selectedSourceId.value = null
    }

    fun selectCategory(category: DiscoverQuickCategory) {
        selectedCatalogId.value = category.pluginId
        selectedSourceId.value = category.sourceId
    }

    fun selectCombined() {
        selectedCatalogId.value = null
        selectedSourceId.value = null
    }

    private data class DiscoverDependencies(
        val homes: Flow<List<CatalogHomeSnapshot>>,
        val rankedStories: Flow<List<RankedCatalogStory>>,
        val refresh: suspend () -> DiscoverRefreshReport,
    )

    private data class DiscoverSelection(
        val pluginId: PluginId?,
        val sourceId: String?,
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

private fun List<CatalogRefreshResult>.toReport(
    homes: List<CatalogHomeSnapshot>,
): DiscoverRefreshReport {
    val refreshedAt = homes.associate { it.pluginId to it.refreshedAtEpochMillis }
    return fold(DiscoverRefreshReport(refreshedAtEpochMillis = refreshedAt)) { report, result ->
        when (result) {
            is CatalogRefreshResult.Success -> report.copy(
                succeeded = report.succeeded + result.pluginId,
            )
            is CatalogRefreshResult.SourceFailure -> report.copy(
                failed = report.failed + (result.pluginId to result.failure.code),
            )
            is CatalogRefreshResult.StoreFailure -> report.copy(
                failed = report.failed + (result.pluginId to result.failure.code),
            )
        }
    }
}
