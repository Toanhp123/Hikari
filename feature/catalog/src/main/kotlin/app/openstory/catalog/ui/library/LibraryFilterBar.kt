package app.openstory.catalog.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.library.LibraryStatus
import app.openstory.designsystem.control.HikariInlineAction
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.layout.HikariModalSheet
import app.openstory.designsystem.layout.HikariSheetContent
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariDimensions

@Composable
internal fun LibraryFilterSheet(
    state: LibraryUiState,
    onDismiss: () -> Unit,
    onStatusSelected: (LibraryStatus?) -> Unit,
    onSourceFilterSelected: (LibrarySourceState?) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onResetFilters: () -> Unit,
) {
    HikariModalSheet(onDismissRequest = onDismiss) {
        HikariSheetContent(title = "Library filters") {
            FilterSection("Status") {
                LibraryChip(
                    selected = state.selectedStatus == null,
                    label = "All ${state.totalCount}",
                    tag = "library-status-all",
                    onClick = { onStatusSelected(null) },
                )
                LibraryStatus.entries.forEach { status ->
                    LibraryChip(
                        selected = state.selectedStatus == status,
                        label = "${status.label()} ${state.statusCounts[status] ?: 0}",
                        tag = "library-status-${status.name.lowercase()}",
                        onClick = { onStatusSelected(status) },
                    )
                }
            }
            FilterSection("Source") {
                LibraryChip(
                    selected = state.sourceFilter == null,
                    label = "All sources",
                    tag = "library-source-all",
                    onClick = { onSourceFilterSelected(null) },
                )
                listOf(LibrarySourceState.LINKED, LibrarySourceState.NO_MAPPING).forEach { source ->
                    LibraryChip(
                        selected = state.sourceFilter == source,
                        label = source.label(),
                        tag = "library-source-${source.name.lowercase()}",
                        onClick = { onSourceFilterSelected(source) },
                    )
                }
            }
            FilterSection("Sort") {
                LibrarySort.entries.forEach { sort ->
                    LibraryChip(
                        selected = state.sort == sort,
                        label = sort.label(),
                        tag = "library-sort-${sort.name.lowercase()}",
                        onClick = { onSortSelected(sort) },
                    )
                }
            }
            HikariInlineAction(
                onClick = onResetFilters,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
            ) {
                Text("Clear filters", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            content = content,
        )
    }
}

@Composable
private fun LibraryChip(
    selected: Boolean,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    HikariFilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag(tag),
    )
}

internal fun LibraryStatus.label(): String = when (this) {
    LibraryStatus.WANT_TO_READ -> "Want to read"
    LibraryStatus.READING -> "Reading"
    LibraryStatus.PAUSED -> "Paused"
    LibraryStatus.COMPLETED -> "Completed"
    LibraryStatus.DROPPED -> "Dropped"
}

internal fun LibrarySort.label(): String = when (this) {
    LibrarySort.LAST_ACTIVITY -> "Recent activity"
    LibrarySort.TITLE -> "Title"
    LibrarySort.DATE_ADDED -> "Date added"
}

internal fun LibraryDisplayMode.label(): String = when (this) {
    LibraryDisplayMode.GRID -> "Grid"
    LibraryDisplayMode.LIST -> "List"
}

internal fun LibrarySourceState.label(): String = when (this) {
    LibrarySourceState.SEARCHING -> "Finding sources"
    LibrarySourceState.LINKED -> "Source linked"
    LibrarySourceState.REVIEW -> "Source review needed"
    LibrarySourceState.NO_MAPPING -> "No source linked"
    LibrarySourceState.FAILED -> "Source search failed"
}
