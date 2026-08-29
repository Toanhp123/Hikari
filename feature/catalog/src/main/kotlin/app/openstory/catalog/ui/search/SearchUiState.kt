package app.openstory.catalog.ui.search

import app.openstory.catalog.search.CatalogSearchFilterGroup
import app.openstory.catalog.search.CatalogSearchResult
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.PluginId

data class SearchUiState(
    val query: String = "",
    val filterGroups: List<CatalogSearchFilterGroup> = emptyList(),
    val filterValues: Map<PluginId, Map<String, List<String>>> = emptyMap(),
    val resultState: SearchResultState = SearchResultState.Idle,
    val filterIssue: CatalogUiFailure? = null,
    val selectionIssue: CatalogUiFailure? = null,
    val recentQueries: List<String> = emptyList(),
)

sealed interface SearchResultState {
    data object Idle : SearchResultState

    data class Active(
        val content: ContentState<CatalogSearchResult>,
    ) : SearchResultState
}
