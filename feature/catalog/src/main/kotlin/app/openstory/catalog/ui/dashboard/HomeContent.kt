package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.withScreenContentInsets
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.surface.HikariContentCardStyle
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun HomeContent(
    content: HomeDashboardContent,
    observationIssue: CatalogUiFailure?,
    onRetryObservation: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onResume: (ReaderTarget) -> Unit,
    continueFocus: FocusRequester,
    readingFocus: FocusRequester,
    firstContentFocusRequester: FocusRequester?,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("home-list"),
        contentPadding = contentPadding.withScreenContentInsets(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.sectionGap),
    ) {
        item("home-summary") { HomeSummary(content.summary) }
        observationIssue?.let { issue ->
            item("home-failure") {
                ObservationFailure(issue, onRetryObservation)
            }
        }
        continueReadingShelf(
            content,
            onResume,
            firstContentFocusRequester ?: continueFocus,
            readingFocus,
        )
        libraryShelves(
            content = content,
            onStorySelected = onStorySelected,
            firstContentFocusRequester = firstContentFocusRequester,
            readingFocusRequester = readingFocus,
        )
        latestUpdatesShelf(
            content.latestUpdates,
            onStorySelected,
            onResume,
            content.firstUpdatesFocus(firstContentFocusRequester),
        )
        if (content.noContentReason == HomeNoContentReason.LIBRARY_PRESENT_BUT_NO_HOME_SECTIONS) {
            item("home-local-empty") { LocalEmptyHome(firstContentFocusRequester) }
        }
    }
}

private fun HomeDashboardContent.firstUpdatesFocus(requester: FocusRequester?): FocusRequester? =
    requester.takeIf {
        continueReading.isEmpty() && reading.isEmpty() && planned.isEmpty() &&
            paused.isEmpty() && completed.isEmpty()
    }

@Composable
private fun LocalEmptyHome(focusRequester: FocusRequester?) {
    HikariContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-local-empty")
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusable(),
        style = HikariContentCardStyle.STANDARD,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text("No active reading shelves yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your Library is intact. Active reading shelves will appear as your statuses change.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
