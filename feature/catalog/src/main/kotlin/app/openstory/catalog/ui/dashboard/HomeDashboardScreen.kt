package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.hikariAtmosphereBrush

@Composable
fun HomeDashboardScreen(
    state: HomeDashboardUiState,
    onDiscover: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onResume: (ReaderTarget) -> Unit,
    firstContentFocusRequester: FocusRequester? = null,
    onUtilityRequested: () -> Unit = {},
    utilityFocusRequester: FocusRequester? = null,
    utilityNextFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val continueFocus = remember { FocusRequester() }
    val readingFocus = remember { FocusRequester() }
    val background = MaterialTheme.hikariAtmosphereBrush
    HikariDestinationScaffold(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
                .testTag("home-atmosphere"),
        ) {
            when {
                state.loading -> Column(Modifier.fillMaxSize().padding(contentPadding)) {
                    HikariTopLevelHeader(
                        title = "Home",
                        onAction = onUtilityRequested,
                        focusRequester = utilityFocusRequester,
                        nextFocusRequester = utilityNextFocusRequester,
                    )
                    HikariLoadingState("Loading your reading home", Modifier.weight(1f))
                }
                state.isEmpty -> Column(Modifier.fillMaxSize().padding(contentPadding)) {
                    HikariTopLevelHeader(
                        title = "Home",
                        onAction = onUtilityRequested,
                        focusRequester = utilityFocusRequester,
                        nextFocusRequester = utilityNextFocusRequester,
                    )
                    Box(Modifier.weight(1f)) {
                        EmptyHome(state.failure, onDiscover, firstContentFocusRequester)
                    }
                }
                else -> HomeContent(
                    state, onStorySelected, onResume, continueFocus, readingFocus,
                    firstContentFocusRequester, contentPadding, onUtilityRequested,
                    utilityFocusRequester, utilityNextFocusRequester,
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(
    failure: HomeDashboardFailure?,
    onDiscover: () -> Unit,
    firstContentFocusRequester: FocusRequester?,
) {
    Box(Modifier.fillMaxSize()) {
        HikariEmptyState(
            title = "Your reading home is ready to grow.",
            message = "Find a story and add it to your Library to begin.",
            actionLabel = "Discover stories",
            onAction = onDiscover,
            actionFocusRequester = firstContentFocusRequester,
        )
        failure?.let { ObservationFailure(it, Modifier.align(Alignment.TopCenter)) }
    }
}
