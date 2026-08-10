package app.openstory.catalog.ui.home

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: CatalogRepository,
    query: CatalogHomeQuery,
    refreshService: CatalogRefreshService,
) : ViewModel() {
    private val dependencies = HomeDependencies(
        homes = repository.observeHomes(),
        rankedStories = query.rankedStories,
        refresh = {
            val results = refreshService.refresh()
            results.toReport(repository.observeHomes().first())
        },
    )
    private val selectedCatalogId = MutableStateFlow<PluginId?>(null)
    private val refreshing = MutableStateFlow(false)
    private val refreshReport = MutableStateFlow<HomeRefreshReport?>(null)

    val state = combine(
        dependencies.homes,
        dependencies.rankedStories,
        selectedCatalogId,
        refreshing,
        refreshReport,
    ) { homes, ranked, selectedId, busy, report ->
        HomeUiState(
            catalogs = homes.sortedBy { it.pluginId.value },
            rankedStories = ranked,
            selectedCatalogId = selectedId,
            refreshing = busy,
            refreshReport = report,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState(),
    )

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                refreshReport.value = dependencies.refresh()
            } finally {
                refreshing.value = false
            }
        }
    }

    fun selectCatalog(pluginId: PluginId) {
        selectedCatalogId.value = pluginId
    }

    fun selectCombined() {
        selectedCatalogId.value = null
    }

    private data class HomeDependencies(
        val homes: Flow<List<CatalogHomeSnapshot>>,
        val rankedStories: Flow<List<RankedCatalogStory>>,
        val refresh: suspend () -> HomeRefreshReport,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun List<CatalogRefreshResult>.toReport(
    homes: List<CatalogHomeSnapshot>,
): HomeRefreshReport {
    val refreshedAt = homes.associate { it.pluginId to it.refreshedAtEpochMillis }
    return fold(HomeRefreshReport(refreshedAtEpochMillis = refreshedAt)) { report, result ->
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
