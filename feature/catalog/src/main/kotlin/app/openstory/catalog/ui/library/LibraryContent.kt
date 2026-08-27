package app.openstory.catalog.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.StoryId
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.layout.HikariResponsiveContent
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.layout.withScreenContentInsets
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariLayoutPolicy
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    focusRequester: FocusRequester,
    onClearFilters: () -> Unit,
    onDiscover: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onRetryContent: () -> Unit,
    onRetryCollection: () -> Unit,
    onRetryObservation: () -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
    gridState: LazyGridState,
    modifier: Modifier,
) {
    when (val content = state.content) {
        ContentState.Pending -> Column(modifier.padding(contentPadding)) {
            HikariLoadingState(
                label = "Loading your Library",
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
        }
        is ContentState.Failed -> Column(modifier.padding(contentPadding)) {
            HikariErrorState(
                title = "Library unavailable",
                message = catalogFailureMessage(
                    content.failure.code,
                    "Couldn't load your Library.",
                ),
                actionLabel = if (content.failure.retryable) "Retry" else null,
                onAction = if (content.failure.retryable) onRetryContent else null,
                modifier = Modifier.weight(1f),
            )
        }
        is ContentState.Ready -> ReadyLibraryContent(
            content = content.value,
            state = state,
            focusRequester = focusRequester,
            onClearFilters = onClearFilters,
            onDiscover = onDiscover,
            onStorySelected = onStorySelected,
            onRetryCollection = onRetryCollection,
            onRetryObservation = onRetryObservation,
            contentPadding = contentPadding,
            listState = listState,
            gridState = gridState,
            modifier = modifier,
        )
    }
}

@Composable
private fun ReadyLibraryContent(
    content: LibraryContent,
    state: LibraryUiState,
    focusRequester: FocusRequester,
    onClearFilters: () -> Unit,
    onDiscover: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onRetryCollection: () -> Unit,
    onRetryObservation: () -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
    gridState: LazyGridState,
    modifier: Modifier,
) {
    when {
        content.totalCount == 0 -> LibraryStateColumn(
            modifier = modifier,
            contentPadding = contentPadding,
            observationIssue = state.observationIssue,
            onRetryObservation = onRetryObservation,
        ) {
            HikariEmptyState(
                title = "Your Library is empty",
                message = "Save stories to build a personal reading collection.",
                actionLabel = "Discover stories",
                onAction = onDiscover,
                actionFocusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
        }
        content.collection is LibraryCollectionState.Resolving -> LibraryStateColumn(
            modifier = modifier,
            contentPadding = contentPadding,
            observationIssue = state.observationIssue,
            onRetryObservation = onRetryObservation,
        ) {
            LibraryCollectionSkeleton(state.displayMode, Modifier.weight(1f))
        }
        content.collection is LibraryCollectionState.Unavailable -> LibraryStateColumn(
            modifier = modifier,
            contentPadding = contentPadding,
            observationIssue = state.observationIssue,
            onRetryObservation = onRetryObservation,
        ) {
            val failure = content.collection.failure
            HikariErrorState(
                title = "Library results unavailable",
                message = catalogFailureMessage(
                    failure.code,
                    "Couldn't apply the current Library controls.",
                ),
                actionLabel = if (failure.retryable) "Retry" else null,
                onAction = if (failure.retryable) onRetryCollection else null,
                modifier = Modifier.weight(1f),
            )
        }
        content.collection is LibraryCollectionState.Ready && content.collection.items.isEmpty() ->
            LibraryStateColumn(
                modifier = modifier,
                contentPadding = contentPadding,
                observationIssue = state.observationIssue,
                onRetryObservation = onRetryObservation,
            ) {
                HikariEmptyState(
                    title = "No stories match these filters",
                    message = "Try a different status, source state, or search.",
                    actionLabel = "Clear filters",
                    onAction = onClearFilters,
                    actionFocusRequester = focusRequester,
                    modifier = Modifier.weight(1f),
                )
            }
        content.collection is LibraryCollectionState.Ready -> LibraryCollection(
            items = content.collection.items,
            state = state,
            observationIssue = state.observationIssue,
            onRetryObservation = onRetryObservation,
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
private fun LibraryStateColumn(
    modifier: Modifier,
    contentPadding: PaddingValues,
    observationIssue: CatalogUiFailure?,
    onRetryObservation: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.padding(contentPadding)) {
        LibraryObservationFeedback(observationIssue, onRetryObservation)
        content()
    }
}

@Composable
private fun LibraryObservationFeedback(
    observationIssue: CatalogUiFailure?,
    onRetryObservation: () -> Unit,
) {
    observationIssue?.let { issue ->
        HikariInlineFeedback(
            message = catalogFailureMessage(
                issue.code,
                "Couldn't update all Library details.",
            ),
            actionLabel = if (issue.retryable) "Retry" else null,
            onAction = if (issue.retryable) onRetryObservation else null,
        )
    }
}

@Composable
private fun LibraryCollection(
    items: List<LibraryItemUiModel>,
    state: LibraryUiState,
    observationIssue: CatalogUiFailure?,
    onRetryObservation: () -> Unit,
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
                contentPadding = contentPadding.withScreenContentInsets(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
            ) {
                if (observationIssue != null) {
                    item(key = "library-observation-feedback") {
                        LibraryObservationFeedback(observationIssue, onRetryObservation)
                    }
                }
                items(items, key = { it.storyId.value }) { item ->
                    LibraryStoryCard(
                        item = item,
                        displayMode = LibraryDisplayMode.LIST,
                        onSelected = { onStorySelected(item.storyId) },
                        focusRequester = firstFocusRequester.takeIf { item == items.first() },
                    )
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
                contentPadding = contentPadding.withScreenContentInsets(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
            ) {
                if (observationIssue != null) {
                    item(
                        key = "library-observation-feedback",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        LibraryObservationFeedback(observationIssue, onRetryObservation)
                    }
                }
                items(items, key = { it.storyId.value }) { item ->
                    LibraryStoryCard(
                        item = item,
                        displayMode = LibraryDisplayMode.GRID,
                        onSelected = { onStorySelected(item.storyId) },
                        focusRequester = firstFocusRequester.takeIf { item == items.first() },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCollectionSkeleton(
    displayMode: LibraryDisplayMode,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("library-collection-skeleton"),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
    ) {
        repeat(if (displayMode == LibraryDisplayMode.LIST) LIST_SKELETON_COUNT else GRID_SKELETON_ROWS) {
            HikariSkeleton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        if (displayMode == LibraryDisplayMode.LIST) {
                            MaterialTheme.hikariDimensions.posterList.height
                        } else {
                            MaterialTheme.hikariDimensions.posterList.height * GRID_SKELETON_HEIGHT_MULTIPLIER
                        },
                    ),
                shape = MaterialTheme.hikariShapes.contentCard,
            )
        }
    }
}

private const val LIST_SKELETON_COUNT = 4
private const val GRID_SKELETON_ROWS = 3
private const val GRID_SKELETON_HEIGHT_MULTIPLIER = 1.6f
