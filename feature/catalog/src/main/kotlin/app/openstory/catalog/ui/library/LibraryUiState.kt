package app.openstory.catalog.ui.library

import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryStatus

data class LibraryUiState(
    val items: List<LibraryItemUiModel> = emptyList(),
    val totalCount: Int = 0,
    val statusCounts: Map<LibraryStatus, Int> = emptyMap(),
    val selectedStatus: LibraryStatus? = null,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.LAST_ACTIVITY,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.GRID,
    val sourceFilter: LibrarySourceState? = null,
    val loading: Boolean = true,
)

data class LibraryItemUiModel(
    val storyId: StoryId,
    val title: String,
    val contentType: ContentType?,
    val coverUrl: String?,
    val status: LibraryStatus,
    val sourceState: LibrarySourceState,
    val progressFraction: Float? = null,
    val addedAt: Long,
    val updatedAt: Long,
)

enum class LibrarySort {
    LAST_ACTIVITY,
    TITLE,
    DATE_ADDED,
}

enum class LibraryDisplayMode {
    GRID,
    LIST,
}

enum class LibrarySourceState {
    SEARCHING,
    LINKED,
    REVIEW,
    NO_MAPPING,
    FAILED,
}
