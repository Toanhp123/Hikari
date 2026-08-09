package app.openstory.home.ui

import androidx.lifecycle.ViewModel
import app.openstory.home.domain.ObserveCombinedHome
import app.openstory.home.domain.RefreshHome
import app.openstory.home.model.HomeCatalog
import app.openstory.home.model.HomeRefreshReport
import app.openstory.home.model.HomeUiModel
import app.openstory.common.id.PluginId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel internal constructor(
    homeFlow: Flow<HomeUiModel>,
    private val refreshAction: suspend () -> HomeRefreshReport,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel() {
    constructor(
        observeHome: ObserveCombinedHome,
        refreshHome: RefreshHome,
    ) : this(
        homeFlow = observeHome(),
        refreshAction = { refreshHome() },
    )

    private val refreshing = MutableStateFlow(false)
    private val refreshReport = MutableStateFlow<HomeRefreshReport?>(null)
    private val selectedCatalogId = MutableStateFlow<PluginId?>(null)

    val state = combine(
        homeFlow,
        refreshing,
        refreshReport,
        selectedCatalogId,
    ) { home, busy, report, selectedId ->
        HomeScreenState(
            home = home,
            refreshing = busy,
            refreshReport = report,
            selectedCatalogId = selectedId,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = HomeScreenState(),
    )

    fun refresh() {
        if (refreshing.value) return

        refreshing.value = true
        scope.launch {
            try {
                refreshReport.value = refreshAction()
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

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}

data class HomeScreenState(
    val home: HomeUiModel = HomeUiModel(
        combined = emptyList(),
        catalogs = emptyList(),
    ),
    val refreshing: Boolean = false,
    val refreshReport: HomeRefreshReport? = null,
    val selectedCatalogId: PluginId? = null,
) {
    val selectedCatalog: HomeCatalog?
        get() = selectedCatalogId?.let { selectedId ->
            home.catalogs.firstOrNull { it.pluginId == selectedId }
        }
}
