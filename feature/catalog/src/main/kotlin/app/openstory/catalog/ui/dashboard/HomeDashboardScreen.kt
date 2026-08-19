package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.HikariTopLevelScaffold
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.hikariAtmosphereBrush
import kotlinx.coroutines.launch

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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop = remember {
        derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_TO_TOP_ITEM_THRESHOLD }
    }
    val headerScrolled = remember {
        derivedStateOf { listState.canScrollBackward }
    }
    val background = MaterialTheme.hikariAtmosphereBrush
    HikariDestinationScaffold(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
                .testTag("home-atmosphere"),
        ) {
            HikariTopLevelScaffold(
                contentPadding = contentPadding,
                header = {
                    HikariTopLevelHeader(
                        title = "Home",
                        onAction = onUtilityRequested,
                        focusRequester = utilityFocusRequester,
                        nextFocusRequester = utilityNextFocusRequester,
                    )
                },
                headerScrolled = !state.loading && !state.isEmpty && headerScrolled.value,
                showScrollToTop = !state.loading && !state.isEmpty && showScrollToTop.value,
                onScrollToTop = { coroutineScope.launch { listState.animateScrollToItem(0) } },
            ) { bodyPadding ->
                when {
                    state.loading -> Column(Modifier.fillMaxSize().padding(bodyPadding)) {
                        HikariLoadingState("Loading your reading home", Modifier.weight(1f))
                    }
                    state.isEmpty -> Column(Modifier.fillMaxSize().padding(bodyPadding)) {
                        Box(Modifier.weight(1f)) {
                            EmptyHome(state.failure, onDiscover, firstContentFocusRequester)
                        }
                    }
                    else -> HomeContent(
                        state = state,
                        onStorySelected = onStorySelected,
                        onResume = onResume,
                        continueFocus = continueFocus,
                        readingFocus = readingFocus,
                        firstContentFocusRequester = firstContentFocusRequester,
                        contentPadding = bodyPadding,
                        listState = listState,
                    )
                }
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

private const val SCROLL_TO_TOP_ITEM_THRESHOLD = 3
