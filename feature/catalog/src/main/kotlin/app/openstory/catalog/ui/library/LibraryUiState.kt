package app.openstory.catalog.ui.library

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryStatus

sealed interface LibraryCollectionState {
    data object Resolving : LibraryCollectionState

    data class Ready(
        val items: List<LibraryItemUiModel>,
    ) : LibraryCollectionState

    data class Unavailable(
        val failure: CatalogUiFailure,
    ) : LibraryCollectionState
}

data class LibraryContent(
    val totalCount: Int,
    val statusCounts: Map<LibraryStatus, Int>,
    val collection: LibraryCollectionState,
)

data class LibraryUiState(
    val content: ContentState<LibraryContent> = ContentState.Pending,
    val selectedStatus: LibraryStatus? = null,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.LAST_ACTIVITY,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.GRID,
    val sourceFilter: LibrarySourceState? = null,
    val observationIssue: CatalogUiFailure? = null,
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
    val publicationStatus: PublicationStatus? = null,
    val score: Score? = null,
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
    UNKNOWN,
    SEARCHING,
    LINKED,
    REVIEW,
    NO_MAPPING,
    FAILED,
}
