package app.openstory.catalog.ui.home

import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.ranking.RankedCatalogStory
import app.openstory.common.id.PluginId

data class HomeUiState(
    val catalogs: List<CatalogHomeSnapshot> = emptyList(),
    val rankedStories: List<RankedCatalogStory> = emptyList(),
    val selectedCatalogId: PluginId? = null,
    val refreshing: Boolean = false,
    val refreshReport: HomeRefreshReport? = null,
) {
    val selectedCatalog: CatalogHomeSnapshot?
        get() = selectedCatalogId?.let { selectedId ->
            catalogs.firstOrNull { it.pluginId == selectedId }
        }
}

data class HomeRefreshReport(
    val succeeded: Set<PluginId> = emptySet(),
    val failed: Map<PluginId, String> = emptyMap(),
    val refreshedAtEpochMillis: Map<PluginId, Long?> = emptyMap(),
)
