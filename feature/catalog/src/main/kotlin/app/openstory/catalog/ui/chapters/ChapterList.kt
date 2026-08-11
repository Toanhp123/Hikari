package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun ChapterList(
    state: ChapterListUiState,
    actions: ChapterListActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = MaterialTheme.hikariSpacing.large),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        chapterListItems(state, actions)
    }
}

fun LazyListScope.chapterListItems(
    state: ChapterListUiState,
    actions: ChapterListActions,
) {
    item(key = "chapter-summary") {
        Text("${state.unreadCount} unread chapters", style = MaterialTheme.typography.titleMedium)
    }
    item(key = "chapter-filters") {
        ChapterFiltersSheet(state, actions)
    }
    item(key = "chapter-download-visible") {
        val visibleReleaseIds = state.chapters.flatMap { chapter -> chapter.releases.map { it.id } }
        TextButton(
            enabled = visibleReleaseIds.isNotEmpty(),
            onClick = { actions.onDownloadFiltered(visibleReleaseIds) },
        ) {
            Text("Download visible")
        }
    }
    state.failure?.let { failure ->
        item(key = "chapter-failure") {
            Text(failure, color = MaterialTheme.colorScheme.error)
        }
    }
    if (state.chapters.isEmpty()) {
        item(key = "chapter-empty") {
            HikariEmptyState(title = "No chapters available")
        }
    }
    items(
        items = state.chapters,
        key = { chapter -> "chapter:${chapter.id.value}" },
    ) { chapter ->
        ChapterRow(chapter, actions)
    }
}

@Composable
private fun ChapterRow(chapter: ChapterItemUiModel, actions: ChapterListActions) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small),
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
                TextButton(onClick = { actions.onDownloadRange(chapter.releases.map { it.id }) }) {
                    Text("Download chapter")
                }
                chapter.releases.forEach { release ->
                    ChapterReleaseRow(
                        release = release,
                        chapterId = chapter.id,
                        onKeepGrouped = actions.onKeepGrouped,
                        onSeparate = actions.onSeparate,
                        onRead = actions.onRead,
                        downloadState = actions.downloadState(release.id),
                        pendingRemoval = actions.pendingRemoval == release.id,
                        downloadActions = actions.downloadActions,
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
