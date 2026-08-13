package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.glass.HikariGlassSurface
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun ChapterList(state: ChapterListUiState, actions: ChapterListActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = MaterialTheme.hikariSpacing.large,
            end = MaterialTheme.hikariSpacing.large,
            bottom = MaterialTheme.hikariSpacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { chapterListItems(state, actions) }
}

fun LazyListScope.chapterListItems(state: ChapterListUiState, actions: ChapterListActions) {
    item(key = "chapter-summary") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Chapters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${state.unreadCount} unread chapters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "chapter-filters") { ChapterFiltersSheet(state, actions) }
    item(key = "chapter-download-visible") {
        val visibleReleaseIds = state.chapters.flatMap { chapter -> chapter.releases.map { it.id } }
        TextButton(
            enabled = visibleReleaseIds.isNotEmpty(),
            onClick = { actions.onDownloadFiltered(visibleReleaseIds) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Download visible") }
    }
    state.failure?.let { failure -> item(key = "chapter-failure") { Text(failure, color = MaterialTheme.colorScheme.error) } }
    if (state.chapters.isEmpty()) item(key = "chapter-empty") { HikariEmptyState(title = "No chapters available") }
    items(state.chapters, key = { "chapter:${it.id.value}" }) { chapter ->
        ChapterRow(chapter, requireNotNull(state.storyId), actions)
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterItemUiModel,
    storyId: app.openstory.common.id.StoryId,
    actions: ChapterListActions,
) {
    HikariGlassSurface(null, Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) { contentDescription = chapter.accessibilityDescription() }
                    .clickable { actions.onToggleExpanded(chapter.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(chapter.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (chapter.tombstoned) "Unavailable" else "Unread",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (chapter.tombstoned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                HikariMetadataBadge("${chapter.releases.size} ${if (chapter.releases.size == 1) "source" else "sources"}")
            }
            if (chapter.expanded) {
                TextButton(
                    onClick = { actions.onDownloadRange(chapter.releases.map { it.id }) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Download chapter") }
                chapter.releases.forEach { release ->
                    ChapterReleaseRow(
                        release, chapter.id, storyId, actions.onKeepGrouped, actions.onSeparate, actions.onRead,
                        actions.downloadState(release.id), actions.pendingRemoval == release.id, actions.downloadActions,
                    )
                }
            }
        }
    }
}

private fun ChapterItemUiModel.accessibilityDescription(): String = buildString {
    append(label)
    append(", ${releases.size}")
    append(if (releases.size == 1) " release" else " releases")
    append(if (tombstoned) ", unavailable" else ", unread")
}
