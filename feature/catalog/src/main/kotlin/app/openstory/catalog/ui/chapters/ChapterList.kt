package app.openstory.catalog.ui.chapters

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.control.HikariInlineAction
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.surface.HikariContentCardStyle
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun ChapterList(
    state: ChapterListUiState,
    actions: ChapterListActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("chapter-list"),
        contentPadding = contentPadding ?: PaddingValues(MaterialTheme.hikariSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) { chapterListItems(state, actions) }
}

fun LazyListScope.chapterListItems(state: ChapterListUiState, actions: ChapterListActions) {
    item(key = "chapter-summary", contentType = "chapter-header") {
        val visibleReleaseIds = state.chapters.flatMap { chapter ->
            chapter.releases.filter(ChapterReleaseUiModel::readerCapable).map(ChapterReleaseUiModel::id)
        }
        HikariSectionHeader(
            title = "Chapters",
            subtitle = "${state.unreadCount} unread chapters",
            action = {
                HikariInlineAction(
                    enabled = visibleReleaseIds.isNotEmpty(),
                    onClick = { actions.onDownloadFiltered(visibleReleaseIds) },
                ) { Text("Download visible") }
            },
        )
    }
    item(key = "chapter-filters", contentType = "chapter-filters") { ChapterFiltersSheet(state, actions) }
    state.failure?.let { failure ->
        item(key = "chapter-failure", contentType = "chapter-feedback") {
            HikariInlineFeedback(message = failure)
        }
    }
    if (state.loading && state.chapters.isEmpty()) {
        item(key = "chapter-loading", contentType = "chapter-progress") {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().testTag("chapter-loading"),
            )
        }
        return
    }
    if (state.chapters.isEmpty()) {
        item(key = "chapter-empty", contentType = "chapter-empty") {
            HikariEmptyState(title = "No chapters available")
        }
        return
    }

    val storyId = requireNotNull(state.storyId)
    state.chapters.forEachIndexed { index, chapter ->
        chapterItems(chapter, storyId, actions, isFirst = index == 0)
    }
}

private fun LazyListScope.chapterItems(
    chapter: ChapterItemUiModel,
    storyId: StoryId,
    actions: ChapterListActions,
    isFirst: Boolean,
) {
    item(
        key = "chapter:${chapter.id.value}",
        contentType = "chapter-summary-card",
    ) {
        ChapterSummaryCard(chapter, actions, isFirst)
    }
    if (!chapter.expanded) return

    item(
        key = "chapter:${chapter.id.value}:download",
        contentType = "chapter-action",
    ) {
        HikariInlineAction(
            onClick = {
                actions.onDownloadRange(
                    chapter.releases.filter(ChapterReleaseUiModel::readerCapable).map(ChapterReleaseUiModel::id),
                )
            },
            enabled = chapter.releases.any(ChapterReleaseUiModel::readerCapable),
        ) { Text("Download chapter") }
    }
    items(
        items = chapter.releases,
        key = { release -> "chapter:${chapter.id.value}:release:${release.id.value}" },
        contentType = { "chapter-release" },
    ) { release ->
        HikariContentCard(modifier = Modifier.fillMaxWidth()) {
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

@Composable
private fun ChapterSummaryCard(
    chapter: ChapterItemUiModel,
    actions: ChapterListActions,
    isFirst: Boolean,
) {
    val benchmarkModifier = if (isFirst) Modifier.testTag("chapter-summary-first") else Modifier
    HikariContentCard(
        modifier = benchmarkModifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = chapter.accessibilityDescription()
        },
        style = HikariContentCardStyle.PROMINENT,
        onClick = { actions.onToggleExpanded(chapter.id) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
                .padding(MaterialTheme.hikariSpacing.space16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
            ) {
                Text(chapter.label, style = MaterialTheme.typography.titleMedium)
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
