package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariMetadataLine
import app.openstory.designsystem.control.HikariIconAction
import app.openstory.designsystem.control.HikariInlineAction
import app.openstory.designsystem.feedback.HikariConfirmDialog
import app.openstory.designsystem.feedback.HikariConfirmationStyle
import app.openstory.designsystem.icon.HikariMoreGlyph
import app.openstory.designsystem.menu.HikariDropdownMenu
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.downloads.DownloadState
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun ChapterReleaseRow(
    release: ChapterReleaseUiModel,
    chapterId: CanonicalChapterId,
    storyId: StoryId,
    onKeepGrouped: (ChapterReleaseId, CanonicalChapterId) -> Unit,
    onSeparate: (ChapterReleaseId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    downloadState: DownloadState?,
    pendingRemoval: Boolean,
    downloadActions: DownloadActions,
    modifier: Modifier = Modifier,
) {
    val offlineReadable = downloadState == DownloadState.COMPLETED
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
            .padding(
                horizontal = MaterialTheme.hikariSpacing.space8,
                vertical = MaterialTheme.hikariSpacing.space4,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
        ) {
            Text(
                text = release.sourceName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HikariMetadataLine(
                items = release.metadataItems(downloadState, offlineReadable),
            )
        }
        if (release.readerCapable || offlineReadable) {
            HikariInlineAction(
                onClick = { onRead(ReaderTarget(storyId, chapterId, release.id)) },
                modifier = Modifier.testTag("chapter-read-${release.id.value}"),
            ) { Text("Read") }
        }
        ChapterReleaseOverflow(
            release = release,
            chapterId = chapterId,
            downloadState = downloadState,
            pendingRemoval = pendingRemoval,
            onKeepGrouped = onKeepGrouped,
            onSeparate = onSeparate,
            downloadActions = downloadActions,
        )
    }
}

@Composable
private fun ChapterReleaseOverflow(
    release: ChapterReleaseUiModel,
    chapterId: CanonicalChapterId,
    downloadState: DownloadState?,
    pendingRemoval: Boolean,
    onKeepGrouped: (ChapterReleaseId, CanonicalChapterId) -> Unit,
    onSeparate: (ChapterReleaseId) -> Unit,
    downloadActions: DownloadActions,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HikariIconAction(
            onClick = { expanded = true },
            contentDescription = "More actions for ${release.sourceName}",
            modifier = Modifier.testTag("chapter-more-${release.id.value}"),
        ) {
            HikariMoreGlyph()
        }
        HikariDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            release.downloadMenuAction(downloadState)?.let { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        expanded = false
                        action.perform(downloadActions, release.id)
                    },
                    modifier = Modifier.testTag("chapter-download-${release.id.value}"),
                )
            }
            DropdownMenuItem(
                text = { Text("Keep grouped") },
                onClick = {
                    expanded = false
                    onKeepGrouped(release.id, chapterId)
                },
            )
            DropdownMenuItem(
                text = { Text("Separate source") },
                onClick = {
                    expanded = false
                    onSeparate(release.id)
                },
            )
        }
    }
    if (pendingRemoval) {
        HikariConfirmDialog(
            title = "Remove offline chapter?",
            message = "The chapter will need network access to open again.",
            confirmLabel = "Remove",
            dismissLabel = "Keep",
            onConfirm = downloadActions.onConfirmRemoval,
            onDismiss = downloadActions.onDismissRemoval,
            style = HikariConfirmationStyle.DESTRUCTIVE,
        )
    }
}

private data class DownloadMenuAction(val label: String, val kind: DownloadMenuKind) {
    fun perform(actions: DownloadActions, releaseId: ChapterReleaseId) {
        when (kind) {
            DownloadMenuKind.DOWNLOAD -> actions.onDownload(releaseId)
            DownloadMenuKind.CANCEL -> actions.onCancel(releaseId)
            DownloadMenuKind.RETRY -> actions.onRetry(releaseId)
            DownloadMenuKind.REMOVE -> actions.onRemove(releaseId)
        }
    }
}

private enum class DownloadMenuKind {
    DOWNLOAD,
    CANCEL,
    RETRY,
    REMOVE,
}

private fun ChapterReleaseUiModel.downloadMenuAction(state: DownloadState?): DownloadMenuAction? = when (state) {
    DownloadState.QUEUED, DownloadState.RUNNING -> DownloadMenuAction("Cancel download", DownloadMenuKind.CANCEL)
    DownloadState.FAILED, DownloadState.CANCELLED -> DownloadMenuAction("Retry download", DownloadMenuKind.RETRY)
    DownloadState.COMPLETED -> DownloadMenuAction("Remove offline", DownloadMenuKind.REMOVE)
    null -> DownloadMenuAction("Download", DownloadMenuKind.DOWNLOAD).takeIf { downloadCapable }
}

private fun ChapterReleaseUiModel.metadataItems(
    downloadState: DownloadState?,
    offlineReadable: Boolean,
): List<String> = listOfNotNull(
    languageLabel,
    publishedAtEpochMillis?.freshnessLabel(),
    downloadState?.statusLabel(),
    when {
        !readerCapable && offlineReadable -> "Offline only"
        !readerCapable -> "List only"
        !downloadCapable -> "Online only"
        else -> null
    },
)

private fun DownloadState.statusLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private val releaseDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC)
private fun Long.freshnessLabel(): String = releaseDateFormatter.format(Instant.ofEpochMilli(this))
