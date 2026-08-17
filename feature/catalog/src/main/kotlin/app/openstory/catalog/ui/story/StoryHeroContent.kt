package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkState
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.control.HikariPrimaryAction
import app.openstory.designsystem.menu.HikariDropdownMenu
import app.openstory.designsystem.theme.hikariColors
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariOpacity
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.LibraryStatus

@Composable
internal fun WideHeroContent(
    story: StoryUiModel,
    artwork: HikariArtworkState,
    libraryStatus: LibraryStatus?,
    readerTarget: ReaderTarget?,
    isResume: Boolean,
    downloadableReleaseId: ChapterReleaseId?,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.hikariSpacing.space20),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16),
        verticalAlignment = Alignment.Bottom,
    ) {
        HikariArtwork(
            state = artwork,
            contentDescription = "${story.preferredTitle} cover",
            modifier = Modifier
                .size(
                    width = MaterialTheme.hikariDimensions.posterDetail.width,
                    height = MaterialTheme.hikariDimensions.posterDetail.height,
                )
                .clip(MaterialTheme.hikariShapes.contentCard),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text(
                text = story.preferredTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.hikariColors.onArtwork,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = story.metadataLabel(),
                color = MaterialTheme.hikariColors.onArtwork.copy(
                    alpha = MaterialTheme.hikariOpacity.onArtworkMuted,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            StoryHeroActions(
                readerTarget = readerTarget,
                isResume = isResume,
                downloadableReleaseId = downloadableReleaseId,
                onRead = onRead,
                onDownload = onDownload,
            )
            LibraryStatusMenu(libraryStatus, onLibraryStatusSelected)
        }
    }
}

@Composable
internal fun NarrowHeroContent(
    story: StoryUiModel,
    artwork: HikariArtworkState,
    libraryStatus: LibraryStatus?,
    readerTarget: ReaderTarget?,
    isResume: Boolean,
    downloadableReleaseId: ChapterReleaseId?,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.hikariSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
            verticalAlignment = Alignment.Bottom,
        ) {
            HikariArtwork(
                state = artwork,
                contentDescription = "${story.preferredTitle} cover",
                modifier = Modifier
                    .size(
                        width = MaterialTheme.hikariDimensions.posterDetailCompact.width,
                        height = MaterialTheme.hikariDimensions.posterDetailCompact.height,
                    )
                    .clip(MaterialTheme.hikariShapes.contentCard),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            ) {
                Text(
                    text = story.preferredTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.hikariColors.onArtwork,
                    maxLines = 3,
                )
                Text(
                    text = story.metadataLabel(),
                    color = MaterialTheme.hikariColors.onArtwork.copy(
                        alpha = MaterialTheme.hikariOpacity.onArtworkMuted,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        StoryHeroActions(
            readerTarget = readerTarget,
            isResume = isResume,
            downloadableReleaseId = downloadableReleaseId,
            onRead = onRead,
            onDownload = onDownload,
        )
        LibraryStatusMenu(libraryStatus, onLibraryStatusSelected)
    }
}

@Composable
private fun StoryHeroActions(
    readerTarget: ReaderTarget?,
    isResume: Boolean,
    downloadableReleaseId: ChapterReleaseId?,
    onRead: (ReaderTarget) -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        HikariPrimaryAction(
            onClick = { readerTarget?.let(onRead) },
            enabled = readerTarget != null,
            modifier = Modifier
                .weight(1f)
                .testTag("story-read"),
        ) {
            Text(if (isResume) "Resume" else "Read")
        }
        if (downloadableReleaseId != null) {
            HikariUtilityAction(
                onClick = { onDownload(downloadableReleaseId) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("story-download"),
            ) {
                Text("Download")
            }
        }
    }
}

@Composable
private fun LibraryStatusMenu(
    status: LibraryStatus?,
    onSelected: (LibraryStatus?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HikariUtilityAction(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("story-library"),
        ) {
            Text(status?.label() ?: "Add to Library")
        }
        HikariDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibraryStatus.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label()) },
                    onClick = {
                        expanded = false
                        onSelected(item)
                    },
                )
            }
            if (status != null) {
                DropdownMenuItem(
                    text = { Text("Remove from Library") },
                    onClick = {
                        expanded = false
                        onSelected(null)
                    },
                )
            }
        }
    }
}

private fun LibraryStatus.label() =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun ContentType.label() =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun StoryUiModel.metadataLabel(): String =
    listOfNotNull(
        contentType.label(),
        score?.let { "${it.value}/${it.scale}" },
    ).joinToString(" · ")
