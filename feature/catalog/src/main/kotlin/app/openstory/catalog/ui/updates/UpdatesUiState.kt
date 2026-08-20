package app.openstory.catalog.ui.updates

import app.openstory.catalog.ui.activity.LibraryActivityItem

data class UpdatesGroupUiModel(
    val label: String,
    val items: List<LibraryActivityItem>,
)

data class UpdatesUiState(
    val groups: List<UpdatesGroupUiModel> = emptyList(),
    val loading: Boolean = true,
    val failure: String? = null,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}
