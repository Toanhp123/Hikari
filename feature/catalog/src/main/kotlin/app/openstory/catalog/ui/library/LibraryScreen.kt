package app.openstory.catalog.ui.library

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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.HikariResponsiveContent
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.library.LibraryStatus

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
    ) 0.dp else 16.dp
    val chrome: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        HikariDestinationScaffold(modifier) {
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
                LibraryEmptyState(
                    "Your Library is empty",
                    "Save stories to build a personal reading collection.",
                    "Discover stories",
                    onDiscover,
                    focusRequester,
                )
            }
        }
        state.items.isEmpty() -> Column(modifier) {
            chrome()
            Box(Modifier.weight(1f)) {
                LibraryEmptyState(
                    "No stories match these filters",
                    "Try a different status, source state, or search.",
                    "Clear filters",
                    onClearFilters,
                    focusRequester,
                )
            }
        }
        else -> LibraryCollection(state, focusRequester, onStorySelected, chrome, modifier)
    }
}

@Composable
private fun LibraryEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    focusRequester: FocusRequester,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.padding(top = 16.dp).focusRequester(focusRequester),
        ) { Text(actionLabel) }
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
        val collectionPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        if (state.displayMode == LibraryDisplayMode.LIST) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("library-chrome") { chrome() }
                items(state.items, key = { it.storyId.value }) { item ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
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
                GridCells.Adaptive(144.dp)
            } else {
                GridCells.Fixed(2)
            }
            LazyVerticalGrid(
                columns = columns,
                contentPadding = collectionPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
