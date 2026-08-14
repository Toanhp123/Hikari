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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.control.HikariContentAction
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.surface.HikariContentCardStyle
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariTypography

@Composable
fun ChapterList(state: ChapterListUiState, actions: ChapterListActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(MaterialTheme.hikariSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space10),
    ) { chapterListItems(state, actions) }
}

fun LazyListScope.chapterListItems(state: ChapterListUiState, actions: ChapterListActions) {
    item(key = "chapter-summary") {
        HikariSectionHeader(
            title = "Chapters",
            subtitle = "${state.unreadCount} unread chapters",
        )
    }
    item(key = "chapter-filters") { ChapterFiltersSheet(state, actions) }
    item(key = "chapter-download-visible") {
        val visibleReleaseIds = state.chapters.flatMap { chapter -> chapter.releases.map { it.id } }
        HikariContentAction(
            enabled = visibleReleaseIds.isNotEmpty(),
            onClick = { actions.onDownloadFiltered(visibleReleaseIds) },
            modifier = Modifier.fillMaxWidth().heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
        ) { Text("Download visible") }
    }
    state.failure?.let { failure ->
        item(key = "chapter-failure") {
            HikariInlineFeedback(message = failure)
        }
    }
    if (state.chapters.isEmpty()) {
        item(key = "chapter-empty") { HikariEmptyState(title = "No chapters available") }
    }
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
    HikariContentCard(
        modifier = Modifier.fillMaxWidth(),
        style = HikariContentCardStyle.PROMINENT,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space14),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
                    .semantics(mergeDescendants = true) { contentDescription = chapter.accessibilityDescription() }
                    .clickable { actions.onToggleExpanded(chapter.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space3),
                ) {
                    Text(chapter.label, style = MaterialTheme.hikariTypography.emphasizedTitleMedium)
                    Text(
                        if (chapter.tombstoned) "Unavailable" else "Unread",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (chapter.tombstoned) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                HikariMetadataBadge(chapter.releaseCountLabel())
            }
            if (chapter.expanded) {
                HikariContentAction(
                    onClick = { actions.onDownloadRange(chapter.releases.map { it.id }) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
                ) { Text("Download chapter") }
                chapter.releases.forEach { release ->
                    ChapterReleaseRow(
                        release = release,
                        chapterId = chapter.id,
                        storyId = storyId,
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

private fun ChapterItemUiModel.releaseCountLabel(): String =
    "${releases.size} ${if (releases.size == 1) "source" else "sources"}"

private fun ChapterItemUiModel.accessibilityDescription(): String = buildString {
    append(label)
    append(", ${releases.size}")
    append(if (releases.size == 1) " release" else " releases")
    append(if (tombstoned) ", unavailable" else ", unread")
}
