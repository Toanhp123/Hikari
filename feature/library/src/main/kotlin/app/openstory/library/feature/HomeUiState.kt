package app.openstory.library.feature

import app.openstory.library.domain.LibraryFilter

data class HomeUiState(
    val inputQuery: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val content: HomeContentState = HomeContentState.Loading,
)

sealed interface HomeContentState {
    data object Loading : HomeContentState
    data object LibraryEmpty : HomeContentState
    data object NoMatches : HomeContentState
    data object Failure : HomeContentState
    data class Content(val stories: List<LibraryStoryPosterUi>) : HomeContentState
}
