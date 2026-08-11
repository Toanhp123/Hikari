package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ChapterList(
    state: ChapterListUiState,
    actions: ChapterListActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${state.unreadCount} unread chapters", style = MaterialTheme.typography.titleMedium)
        ChapterFiltersSheet(state, actions)
        state.failure?.let { failure ->
            Text(failure, color = MaterialTheme.colorScheme.error)
        }
        if (state.chapters.isEmpty()) {
            Text("No chapters available", style = MaterialTheme.typography.bodyMedium)
        }
        state.chapters.forEach { chapter -> ChapterRow(chapter, actions) }
    }
}

@Composable
private fun ChapterRow(chapter: ChapterItemUiModel, actions: ChapterListActions) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = chapter.accessibilityDescription()
                    }
                    .clickable { actions.onToggleExpanded(chapter.id) },
            ) {
                Text(chapter.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (chapter.tombstoned) "Unavailable · ${chapter.releases.size} releases"
                    else "${chapter.releases.size} releases",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (chapter.expanded) {
                chapter.releases.forEach { release ->
                    ChapterReleaseRow(
                        release = release,
                        chapterId = chapter.id,
                        onKeepGrouped = actions.onKeepGrouped,
                        onSeparate = actions.onSeparate,
                    )
                }
            }
        }
    }
}

private fun ChapterItemUiModel.accessibilityDescription(): String = buildString {
    append(label)
    append(", ")
    append(releases.size)
    append(if (releases.size == 1) " release" else " releases")
    append(if (tombstoned) ", unavailable" else ", unread")
}
