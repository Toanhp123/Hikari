package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChapterFiltersSheet(
    state: ChapterListUiState,
    actions: ChapterListActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.selectedFilter == ChapterListFilter.ALL,
            onClick = { actions.onFilterSelected(ChapterListFilter.ALL) },
            label = { Text("All") },
        )
        FilterChip(
            selected = state.selectedFilter == ChapterListFilter.MULTI_RELEASE,
            onClick = { actions.onFilterSelected(ChapterListFilter.MULTI_RELEASE) },
            label = { Text("Multi-source") },
        )
        Checkbox(
            checked = state.showTombstones,
            onCheckedChange = actions.onTombstonesVisible,
        )
        Text("Unavailable")
    }
}
