package app.openstory.catalog.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.common.id.StoryId
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.LibraryStatus

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onStatusSelected: (LibraryStatus?) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onStorySelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.hikariSpacing.large,
                vertical = MaterialTheme.hikariSpacing.medium,
            ),
        )
        StatusFilters(state.selectedStatus, onStatusSelected)
        SortControls(state.sort, onSortSelected)
        if (state.items.isEmpty()) {
            HikariEmptyState(
                title = if (state.selectedStatus == null) {
                    "Your Library is empty."
                } else {
                    "No stories with this status."
                },
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.hikariSpacing.large,
                    vertical = MaterialTheme.hikariSpacing.medium,
                ),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = state.items,
                    key = { item -> item.storyId.value },
                ) { item ->
                    LibraryItem(item = item, onSelected = { onStorySelected(item.storyId) })
                }
            }
        }
    }
}

@Composable
private fun StatusFilters(
    selectedStatus: LibraryStatus?,
    onStatusSelected: (LibraryStatus?) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.hikariSpacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small),
    ) {
        FilterChip(
            selected = selectedStatus == null,
            onClick = { onStatusSelected(null) },
            label = { Text("All") },
        )
        LibraryStatus.entries.forEach { status ->
            FilterChip(
                selected = selectedStatus == status,
                onClick = { onStatusSelected(status) },
                label = { Text(status.label()) },
            )
        }
    }
}

@Composable
private fun SortControls(
    selectedSort: LibrarySort,
    onSortSelected: (LibrarySort) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = MaterialTheme.hikariSpacing.large,
                vertical = MaterialTheme.hikariSpacing.small,
            ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small),
    ) {
        LibrarySort.entries.forEach { sort ->
            FilterChip(
                selected = selectedSort == sort,
                onClick = { onSortSelected(sort) },
                label = { Text(sort.label()) },
            )
        }
    }
}

@Composable
private fun LibraryItem(
    item: LibraryItemUiModel,
    onSelected: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelected)
            .semantics(mergeDescendants = true) {
                contentDescription = item.accessibilityDescription()
            }
            .padding(
                horizontal = MaterialTheme.hikariSpacing.large,
                vertical = MaterialTheme.hikariSpacing.medium,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.extraSmall),
    ) {
        Text(item.title, style = MaterialTheme.typography.titleMedium)
        Text(item.status.label(), style = MaterialTheme.typography.bodyMedium)
        Text(item.sourceState.label(), style = MaterialTheme.typography.bodySmall)
    }
}

private fun LibraryItemUiModel.accessibilityDescription(): String =
    "$title. ${status.label()}. ${sourceState.label()}."

private fun LibraryStatus.label(): String = when (this) {
    LibraryStatus.WANT_TO_READ -> "Want to read"
    LibraryStatus.READING -> "Reading"
    LibraryStatus.PAUSED -> "Paused"
    LibraryStatus.COMPLETED -> "Completed"
    LibraryStatus.DROPPED -> "Dropped"
}

private fun LibrarySort.label(): String = when (this) {
    LibrarySort.LAST_ACTIVITY -> "Recent activity"
    LibrarySort.TITLE -> "Title"
    LibrarySort.DATE_ADDED -> "Date added"
}

private fun LibrarySourceState.label(): String = when (this) {
    LibrarySourceState.SEARCHING -> "Finding sources"
    LibrarySourceState.LINKED -> "Source linked"
    LibrarySourceState.REVIEW -> "Source review needed"
    LibrarySourceState.NO_MAPPING -> "No source linked"
    LibrarySourceState.FAILED -> "Source search failed"
}
