package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.download.DownloadActionSheet
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.downloads.DownloadState
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariOpacity
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariTypography

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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.hikariShapes.compactCard,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = MaterialTheme.hikariOpacity.subtleSurface),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space12),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text(release.sourceName, style = MaterialTheme.hikariTypography.emphasizedTitleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space6)) {
                HikariMetadataBadge(release.languageLabel)
                release.publishedAtEpochMillis?.let { HikariMetadataBadge(it.freshnessLabel()) }
                downloadState?.let { HikariMetadataBadge(it.name.lowercase().replaceFirstChar(Char::uppercase)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
            ) {
                TextButton(
                    onClick = { onRead(ReaderTarget(storyId, chapterId, release.id)) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
                        .testTag("chapter-read-${release.id.value}"),
                ) { Text("Read") }
                DownloadActionSheet(
                    release.id,
                    downloadState,
                    pendingRemoval,
                    downloadActions,
                    modifier = Modifier.weight(1f),
                    actionTag = "chapter-download-${release.id.value}",
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { onKeepGrouped(release.id, chapterId) },
                    modifier = Modifier.heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
                ) { Text("Keep grouped") }
                TextButton(
                    onClick = { onSeparate(release.id) },
                    modifier = Modifier.heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
                ) { Text("Separate") }
            }
        }
    }
}

private val releaseDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC)
private fun Long.freshnessLabel(): String = releaseDateFormatter.format(Instant.ofEpochMilli(this))
