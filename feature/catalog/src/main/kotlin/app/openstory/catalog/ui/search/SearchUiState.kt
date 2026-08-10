package app.openstory.catalog.ui.search

import app.openstory.catalog.search.CatalogSearchFailure
import app.openstory.catalog.search.CatalogSearchFilterGroup
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.common.id.PluginId

data class SearchUiState(
    val query: String = "",
    val filterGroups: List<CatalogSearchFilterGroup> = emptyList(),
    val filterValues: Map<PluginId, Map<String, List<String>>> = emptyMap(),
    val stories: List<CatalogSearchStory> = emptyList(),
    val failures: List<CatalogSearchFailure> = emptyList(),
    val searching: Boolean = false,
    val recentQueries: List<String> = emptyList(),
)
