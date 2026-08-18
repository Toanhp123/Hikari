package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.plus
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun HomeContent(
    state: HomeDashboardUiState,
    onStorySelected: (StoryId) -> Unit,
    onResume: (ReaderTarget) -> Unit,
    continueFocus: FocusRequester,
    readingFocus: FocusRequester,
    firstContentFocusRequester: FocusRequester?,
    contentPadding: PaddingValues,
    onUtilityRequested: () -> Unit,
    utilityFocusRequester: FocusRequester?,
    utilityNextFocusRequester: FocusRequester?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding.plus(bottom = MaterialTheme.hikariSpacing.space24),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16),
    ) {
        item("home-header") {
            HikariTopLevelHeader(
                title = "Home",
                onAction = onUtilityRequested,
                focusRequester = utilityFocusRequester,
                nextFocusRequester = utilityNextFocusRequester,
            )
        }
        item("home-summary") { HomeSummary(state.summary) }
        state.failure?.let { failure ->
            item("home-failure") {
                ObservationFailure(failure, Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space20))
            }
        }
        continueReadingShelf(
            state,
            onResume,
            firstContentFocusRequester ?: continueFocus,
            readingFocus,
        )
        libraryShelves(
            state = state,
            onStorySelected = onStorySelected,
            firstContentFocusRequester = firstContentFocusRequester,
            readingFocusRequester = readingFocus,
        )
        latestUpdatesShelf(
            state.latestUpdates,
            onStorySelected,
            onResume,
            state.firstUpdatesFocus(firstContentFocusRequester),
        )
    }
}

private fun HomeDashboardUiState.firstUpdatesFocus(requester: FocusRequester?): FocusRequester? =
    requester.takeIf {
        continueReading.isEmpty() && reading.isEmpty() && planned.isEmpty() &&
            paused.isEmpty() && completed.isEmpty()
    }
