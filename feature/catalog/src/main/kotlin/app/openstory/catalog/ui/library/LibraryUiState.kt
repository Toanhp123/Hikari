package app.openstory.catalog.ui.library

import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryStatus

data class LibraryUiState(
    val items: List<LibraryItemUiModel> = emptyList(),
    val selectedStatus: LibraryStatus? = null,
    val sort: LibrarySort = LibrarySort.LAST_ACTIVITY,
)

data class LibraryItemUiModel(
    val storyId: StoryId,
    val title: String,
    val contentType: ContentType?,
    val coverUrl: String?,
    val status: LibraryStatus,
    val sourceState: LibrarySourceState,
    val addedAt: Long,
    val updatedAt: Long,
)

enum class LibrarySort {
    LAST_ACTIVITY,
    TITLE,
    DATE_ADDED,
}

enum class LibrarySourceState {
    SEARCHING,
    LINKED,
    REVIEW,
    NO_MAPPING,
    FAILED,
}
