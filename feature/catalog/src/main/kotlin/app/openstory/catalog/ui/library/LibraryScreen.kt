package app.openstory.catalog.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.HikariResponsiveContent
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.state.HikariLoadingState
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
    firstFilterFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val defaultFirstFilterFocus = remember { FocusRequester() }
    val firstFilterFocus = firstFilterFocusRequester ?: defaultFirstFilterFocus
    val statusFocus = remember { FocusRequester() }
    val sourceFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val displayFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
            LibraryFilterBar(
                state = state,
                onQueryChange = onQueryChange,
                onStatusSelected = onStatusSelected,
                onSourceFilterSelected = onSourceFilterSelected,
                onSortSelected = onSortSelected,
                onDisplayModeSelected = onDisplayModeSelected,
                firstFilterFocusRequester = firstFilterFocus,
                statusFocusRequester = statusFocus,
                sourceFocusRequester = sourceFocus,
                sortFocusRequester = sortFocus,
                displayFocusRequester = displayFocus,
                contentFocusRequester = contentFocus,
            )
            when {
                state.loading -> HikariLoadingState(
                    label = "Loading your Library",
                    modifier = Modifier.focusRequester(contentFocus),
                )
                state.totalCount == 0 -> LibraryEmptyState(
                    title = "Your Library is empty",
                    message = "Save stories to build a personal reading collection.",
                    actionLabel = "Discover stories",
                    onAction = onDiscover,
                    focusRequester = contentFocus,
                )
                state.items.isEmpty() -> LibraryEmptyState(
                    title = "No stories match these filters",
                    message = "Try a different status, source state, or search.",
                    actionLabel = "Clear filters",
                    onAction = onClearFilters,
                    focusRequester = contentFocus,
                )
                else -> LibraryCollection(state, contentFocus, onStorySelected)
            }
        }
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
) {
    HikariResponsiveContent(Modifier.fillMaxSize()) {
        val contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp)
        if (state.displayMode == LibraryDisplayMode.LIST) {
            LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.storyId.value }) { item ->
                    LibraryStoryCard(
                        item = item,
                        displayMode = LibraryDisplayMode.LIST,
                        onSelected = { onStorySelected(item.storyId) },
                        focusRequester = firstFocusRequester.takeIf { item == state.items.first() },
                    )
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
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
