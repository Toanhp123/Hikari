package app.openstory.catalog.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.HikariTopLevelScaffold
import app.openstory.designsystem.theme.hikariAtmosphereBrush
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.LibraryStatus
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onStatusSelected: (LibraryStatus?) -> Unit,
    onSourceFilterSelected: (LibrarySourceState?) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onDisplayModeSelected: (LibraryDisplayMode) -> Unit,
    onClearFilters: () -> Unit,
    onDiscover: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onResetFilters: () -> Unit = onClearFilters,
    firstFilterFocusRequester: FocusRequester? = null,
    onUtilityRequested: () -> Unit = {},
    utilityFocusRequester: FocusRequester? = null,
    utilityNextFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val defaultFirstFilterFocus = remember { FocusRequester() }
    val firstFilterFocus = firstFilterFocusRequester ?: defaultFirstFilterFocus
    val filterFocus = remember { FocusRequester() }
    val viewFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }
    val displayMode = state.displayMode
    val hasItems = state.items.isNotEmpty()
    val showScrollToTop by remember(displayMode, hasItems) {
        derivedStateOf {
            val firstVisibleItemIndex = when (displayMode) {
                LibraryDisplayMode.LIST -> listState.firstVisibleItemIndex
                LibraryDisplayMode.GRID -> gridState.firstVisibleItemIndex
            }
            hasItems && firstVisibleItemIndex >= SCROLL_TO_TOP_ITEM_THRESHOLD
        }
    }
    val headerScrolled by remember(displayMode) {
        derivedStateOf {
            when (displayMode) {
                LibraryDisplayMode.LIST -> listState.canScrollBackward
                LibraryDisplayMode.GRID -> gridState.canScrollBackward
            }
        }
    }
    val chrome: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.sectionContentGap)) {
            HikariTopLevelHeader(
                title = "Library",
                onAction = onUtilityRequested,
                focusRequester = utilityFocusRequester,
                nextFocusRequester = utilityNextFocusRequester,
            )
            LibraryToolbar(
                query = state.query,
                displayMode = state.displayMode,
                onQueryChange = onQueryChange,
                onFilterRequested = { showFilters = true },
                onDisplayModeSelected = onDisplayModeSelected,
                searchFocusRequester = firstFilterFocus,
                filterFocusRequester = filterFocus,
                viewFocusRequester = viewFocus,
                contentFocusRequester = contentFocus,
                horizontalPadding = MaterialTheme.hikariSpacing.screenGutter,
            )
        }
    }
    val background = MaterialTheme.hikariAtmosphereBrush
    HikariDestinationScaffold(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
                .testTag("library-atmosphere"),
        ) {
            HikariTopLevelScaffold(
                contentPadding = contentPadding,
                header = chrome,
                headerScrolled = headerScrolled,
                showScrollToTop = showScrollToTop,
                onScrollToTop = {
                    coroutineScope.launch {
                        when (state.displayMode) {
                            LibraryDisplayMode.LIST -> listState.animateScrollToItem(0)
                            LibraryDisplayMode.GRID -> gridState.animateScrollToItem(0)
                        }
                    }
                },
            ) { bodyPadding ->
                LibraryContent(
                    state = state,
                    focusRequester = contentFocus,
                    onClearFilters = onClearFilters,
                    onDiscover = onDiscover,
                    onStorySelected = onStorySelected,
                    contentPadding = bodyPadding,
                    listState = listState,
                    gridState = gridState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    if (showFilters) {
        LibraryFilterSheet(
            state = state,
            onDismiss = { showFilters = false },
            onStatusSelected = onStatusSelected,
            onSourceFilterSelected = onSourceFilterSelected,
            onSortSelected = onSortSelected,
            onResetFilters = onResetFilters,
        )
    }
}


private const val SCROLL_TO_TOP_ITEM_THRESHOLD = 3
