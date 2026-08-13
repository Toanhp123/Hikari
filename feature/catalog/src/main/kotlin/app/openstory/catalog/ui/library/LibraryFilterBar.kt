package app.openstory.catalog.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.openstory.library.LibraryStatus

@Composable
internal fun LibraryFilterBar(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onStatusSelected: (LibraryStatus?) -> Unit,
    onSourceFilterSelected: (LibrarySourceState?) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onDisplayModeSelected: (LibraryDisplayMode) -> Unit,
    firstFilterFocusRequester: FocusRequester,
    statusFocusRequester: FocusRequester,
    sourceFocusRequester: FocusRequester,
    sortFocusRequester: FocusRequester,
    displayFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text("Search your Library") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 48.dp)
                .focusRequester(firstFilterFocusRequester)
                .focusProperties { down = statusFocusRequester }
                .testTag("library-query"),
        )
        FilterRow {
            LibraryChip(
                selected = state.selectedStatus == null,
                label = "All ${state.totalCount}",
                tag = "library-status-all",
                onClick = { onStatusSelected(null) },
                modifier = Modifier.focusRequester(statusFocusRequester).focusProperties { down = sourceFocusRequester },
            )
            LibraryStatus.entries.forEach { status ->
                LibraryChip(
                    selected = state.selectedStatus == status,
                    label = "${status.label()} ${state.statusCounts[status] ?: 0}",
                    tag = "library-status-${status.name.lowercase()}",
                    onClick = { onStatusSelected(status) },
                    modifier = Modifier.focusProperties { down = sourceFocusRequester },
                )
            }
        }
        FilterRow {
            LibraryChip(
                selected = state.sourceFilter == null,
                label = "All sources",
                tag = "library-source-all",
                onClick = { onSourceFilterSelected(null) },
                modifier = Modifier.focusRequester(sourceFocusRequester).focusProperties { down = sortFocusRequester },
            )
            listOf(LibrarySourceState.LINKED, LibrarySourceState.NO_MAPPING).forEach { source ->
                LibraryChip(
                    selected = state.sourceFilter == source,
                    label = source.label(),
                    tag = "library-source-${source.name.lowercase()}",
                    onClick = { onSourceFilterSelected(source) },
                    modifier = Modifier.focusProperties { down = sortFocusRequester },
                )
            }
        }
        FilterRow {
            LibrarySort.entries.forEachIndexed { index, sort ->
                LibraryChip(
                    selected = state.sort == sort,
                    label = sort.label(),
                    tag = "library-sort-${sort.name.lowercase()}",
                    onClick = { onSortSelected(sort) },
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(sortFocusRequester) else Modifier)
                        .focusProperties { down = displayFocusRequester },
                )
            }
        }
        FilterRow {
            LibraryDisplayMode.entries.forEachIndexed { index, mode ->
                LibraryChip(
                    selected = state.displayMode == mode,
                    label = mode.label(),
                    tag = "library-display-${mode.name.lowercase()}",
                    onClick = { onDisplayModeSelected(mode) },
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(displayFocusRequester) else Modifier)
                        .focusProperties { down = contentFocusRequester },
                )
            }
        }
    }
}

@Composable
private fun FilterRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun LibraryChip(
    selected: Boolean,
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.heightIn(min = 48.dp).testTag(tag),
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
