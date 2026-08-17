package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun ChapterFiltersSheet(
    state: ChapterListUiState,
    actions: ChapterListActions,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        HikariFilterChip(
            selected = state.selectedFilter == ChapterListFilter.ALL,
            onClick = { actions.onFilterSelected(ChapterListFilter.ALL) },
            label = { Text("All") },
        )
        HikariFilterChip(
            selected = state.selectedFilter == ChapterListFilter.MULTI_RELEASE,
            onClick = { actions.onFilterSelected(ChapterListFilter.MULTI_RELEASE) },
            label = { Text("Multi-source") },
        )
        HikariFilterChip(
            selected = state.showTombstones,
            onClick = { actions.onTombstonesVisible(!state.showTombstones) },
            label = { Text("Unavailable") },
        )
    }
}
