package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.download.DownloadActionSheet
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariMetadataBadgeGroup
import app.openstory.designsystem.control.HikariInlineAction
import app.openstory.designsystem.control.HikariUtilityAction
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.hikariSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        Text(release.sourceName, style = MaterialTheme.typography.titleSmall)
        HikariMetadataBadgeGroup(
            listOfNotNull(
                release.languageLabel,
                release.publishedAtEpochMillis?.freshnessLabel(),
                downloadState?.name?.lowercase()?.replaceFirstChar(Char::uppercase),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            HikariUtilityAction(
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            HikariInlineAction(
                onClick = { onKeepGrouped(release.id, chapterId) },
                modifier = Modifier.weight(1f),
            ) { Text("Keep grouped") }
            HikariInlineAction(
                onClick = { onSeparate(release.id) },
                modifier = Modifier.weight(1f),
            ) { Text("Separate") }
        }
    }
}

private val releaseDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC)
private fun Long.freshnessLabel(): String = releaseDateFormatter.format(Instant.ofEpochMilli(this))
