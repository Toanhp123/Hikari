package app.openstory.catalog.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.HikariResponsiveContent
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.theme.hikariAtmosphereBrush
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.LibraryStatus
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariLayoutPolicy

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
    var showFilters by remember { mutableStateOf(false) }
    val embeddedGridPadding = if (
        state.displayMode == LibraryDisplayMode.GRID && state.items.isNotEmpty()
    ) MaterialTheme.hikariDimensions.zero else MaterialTheme.hikariSpacing.space16
    val chrome: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
            HikariTopLevelHeader(
                title = "Library",
                horizontalPadding = embeddedGridPadding,
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
                horizontalPadding = embeddedGridPadding,
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
            LibraryContent(
                state = state,
                focusRequester = contentFocus,
                onClearFilters = onClearFilters,
                onDiscover = onDiscover,
                onStorySelected = onStorySelected,
                chrome = chrome,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
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

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    focusRequester: FocusRequester,
    onClearFilters: () -> Unit,
    onDiscover: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    chrome: @Composable () -> Unit,
    modifier: Modifier,
) {
    when {
        state.loading -> Column(modifier) {
            chrome()
            HikariLoadingState(
                label = "Loading your Library",
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
        }
        state.totalCount == 0 -> Column(modifier) {
            chrome()
            Box(Modifier.weight(1f)) {
                HikariEmptyState(
                    title = "Your Library is empty",
                    message = "Save stories to build a personal reading collection.",
                    actionLabel = "Discover stories",
                    onAction = onDiscover,
                    actionFocusRequester = focusRequester,
                )
            }
        }
        state.items.isEmpty() -> Column(modifier) {
            chrome()
            Box(Modifier.weight(1f)) {
                HikariEmptyState(
                    title = "No stories match these filters",
                    message = "Try a different status, source state, or search.",
                    actionLabel = "Clear filters",
                    onAction = onClearFilters,
                    actionFocusRequester = focusRequester,
                )
            }
        }
        else -> LibraryCollection(state, focusRequester, onStorySelected, chrome, modifier)
    }
}

@Composable
private fun LibraryCollection(
    state: LibraryUiState,
    firstFocusRequester: FocusRequester,
    onStorySelected: (StoryId) -> Unit,
    chrome: @Composable () -> Unit,
    modifier: Modifier,
) {
    HikariResponsiveContent(modifier) {
        val collectionPadding = PaddingValues(
            start = MaterialTheme.hikariSpacing.space16,
            end = MaterialTheme.hikariSpacing.space16,
            bottom = MaterialTheme.hikariSpacing.space24,
        )
        if (state.displayMode == LibraryDisplayMode.LIST) {
            LazyColumn(
                modifier = Modifier.testTag("library-collection"),
                contentPadding = PaddingValues(bottom = MaterialTheme.hikariSpacing.space24),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
            ) {
                item("library-chrome") { chrome() }
                items(state.items, key = { it.storyId.value }) { item ->
                    Box(Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space16)) {
                        LibraryStoryCard(
                            item = item,
                            displayMode = LibraryDisplayMode.LIST,
                            onSelected = { onStorySelected(item.storyId) },
                            focusRequester = firstFocusRequester.takeIf { item == state.items.first() },
                        )
                    }
                }
            }
        } else {
            val columns = if (windowClass == HikariWindowClass.MEDIUM) {
                GridCells.Adaptive(MaterialTheme.hikariDimensions.adaptiveGridMinCell)
            } else {
                GridCells.Fixed(MaterialTheme.hikariLayoutPolicy.compactGridColumns)
            }
            LazyVerticalGrid(
                columns = columns,
                modifier = Modifier.testTag("library-collection"),
                contentPadding = collectionPadding,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16),
            ) {
                item(key = "library-chrome", span = { GridItemSpan(maxLineSpan) }) { chrome() }
                items(state.items, key = { it.storyId.value }) { item ->
                    LibraryStoryCard(
                        item = item,
                        displayMode = LibraryDisplayMode.GRID,
                        onSelected = { onStorySelected(item.storyId) },
                        focusRequester = firstFocusRequester.takeIf { item == state.items.first() },
                    )
                }
            }
        }
    }
}
