package app.openstory.catalog.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.HikariResponsiveContent
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.layout.plus
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariLayoutPolicy
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    focusRequester: FocusRequester,
    onClearFilters: () -> Unit,
    onDiscover: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
    gridState: LazyGridState,
    modifier: Modifier,
) {
    when {
        state.loading -> Column(modifier.padding(contentPadding)) {
            HikariLoadingState(
                label = "Loading your Library",
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
        }
        state.totalCount == 0 -> Column(modifier.padding(contentPadding)) {
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
        state.items.isEmpty() -> Column(modifier.padding(contentPadding)) {
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
        else -> LibraryCollection(
            state = state,
            firstFocusRequester = focusRequester,
            onStorySelected = onStorySelected,
            contentPadding = contentPadding,
            listState = listState,
            gridState = gridState,
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryCollection(
    state: LibraryUiState,
    firstFocusRequester: FocusRequester,
    onStorySelected: (StoryId) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
    gridState: LazyGridState,
    modifier: Modifier,
) {
    HikariResponsiveContent(modifier) {
        if (state.displayMode == LibraryDisplayMode.LIST) {
            LazyColumn(
                state = listState,
                modifier = Modifier.testTag("library-collection"),
                contentPadding = contentPadding.plus(bottom = MaterialTheme.hikariSpacing.space24),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
            ) {
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
                state = gridState,
                modifier = Modifier.testTag("library-collection"),
                contentPadding = contentPadding.plus(
                    start = MaterialTheme.hikariSpacing.space16,
                    end = MaterialTheme.hikariSpacing.space16,
                    bottom = MaterialTheme.hikariSpacing.space24,
                ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16),
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
