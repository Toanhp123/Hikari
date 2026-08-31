package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    libraryStatusResolved: Boolean,
    primaryReadAction: StoryPrimaryReadAction,
    downloadableReleaseId: ChapterReleaseId?,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onFindSource: () -> Unit,
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
            StoryHeroMetadata(story, compact = false)
            StoryHeroActions(
                libraryStatus = libraryStatus,
                libraryStatusResolved = libraryStatusResolved,
                primaryReadAction = primaryReadAction,
                downloadableReleaseId = downloadableReleaseId,
                onLibraryStatusSelected = onLibraryStatusSelected,
                onRead = onRead,
                onFindSource = onFindSource,
                onDownload = onDownload,
            )
        }
    }
}

@Composable
internal fun NarrowHeroContent(
    story: StoryUiModel,
    artwork: HikariArtworkState,
    libraryStatus: LibraryStatus?,
    libraryStatusResolved: Boolean,
    primaryReadAction: StoryPrimaryReadAction,
    downloadableReleaseId: ChapterReleaseId?,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onFindSource: () -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.hikariSpacing.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
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
                .clip(MaterialTheme.hikariShapes.contentCard)
                .testTag("story-hero-cover"),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            StoryHeroMetadata(story, compact = true)
            StoryHeroActions(
                libraryStatus = libraryStatus,
                libraryStatusResolved = libraryStatusResolved,
                primaryReadAction = primaryReadAction,
                downloadableReleaseId = downloadableReleaseId,
                onLibraryStatusSelected = onLibraryStatusSelected,
                onRead = onRead,
                onFindSource = onFindSource,
                onDownload = onDownload,
            )
        }
    }
}


@Composable
private fun StoryHeroMetadata(story: StoryUiModel, compact: Boolean) {
    Text(
        text = story.preferredTitle,
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.hikariColors.onArtwork,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = story.metadataLabel(),
        color = MaterialTheme.hikariColors.onArtwork.copy(
            alpha = MaterialTheme.hikariOpacity.onArtworkMuted,
        ),
        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
    )
}

private fun ContentType.label() =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun StoryUiModel.metadataLabel(): String =
    listOfNotNull(
        contentType.label(),
        score?.let { "${it.value}/${it.scale}" },
    ).joinToString(" · ")
