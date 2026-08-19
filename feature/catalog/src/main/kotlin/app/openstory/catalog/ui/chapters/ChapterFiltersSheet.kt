package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.control.HikariCompactAction
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.layout.HikariModalSheet
import app.openstory.designsystem.layout.HikariSheetContent
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun ChapterFiltersSheet(
    state: ChapterListUiState,
    actions: ChapterListActions,
    visibleReleaseIds: List<ChapterReleaseId>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HikariModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        HikariSheetContent(title = "Chapter options") {
            ChapterControlSection("Filters") {
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
            ChapterControlSection("Actions") {
                HikariCompactAction(
                    onClick = {
                        actions.onDownloadFiltered(visibleReleaseIds)
                        onDismiss()
                    },
                    enabled = visibleReleaseIds.isNotEmpty(),
                ) {
                    Text("Download visible")
                }
            }
        }
    }
}

@Composable
private fun ChapterControlSection(
    title: String,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            content = content,
        )
    }
}
