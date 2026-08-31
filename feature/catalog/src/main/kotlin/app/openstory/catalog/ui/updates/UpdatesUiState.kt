package app.openstory.catalog.ui.updates

import app.openstory.catalog.ui.activity.LibraryActivityItem
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState

data class UpdatesGroupUiModel(
    val label: String,
    val items: List<LibraryActivityItem>,
)

data class UpdatesContent(
    val groups: List<UpdatesGroupUiModel> = emptyList(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

data class UpdatesUiState(
    val content: ContentState<UpdatesContent> = ContentState.Pending,
    val observationIssue: CatalogUiFailure? = null,
)
