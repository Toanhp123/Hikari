package app.universalmedia.feature.library

import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.RootId

data class LibraryCardUi(val mediaId: MediaId, val displayName: String?) {
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: "Untitled video"
}

enum class LibraryScanStatus {
    IDLE,
    RUNNING,
    COMPLETE,
    PARTIAL,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    ACCESS_LOST,
    UNAVAILABLE,
}

data class LibraryRootUi(val rootId: RootId, val status: LibraryScanStatus)

enum class LibraryError { REGISTRATION, STORAGE, SCHEDULING, LIBRARY }

data class LibraryUiState(
    val cards: List<LibraryCardUi> = emptyList(),
    val roots: List<LibraryRootUi> = emptyList(),
    val isAddingRoot: Boolean = false,
    val error: LibraryError? = null,
)
