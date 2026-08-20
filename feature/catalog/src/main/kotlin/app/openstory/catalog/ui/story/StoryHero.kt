package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.artwork.HikariArtworkBackdrop
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.library.LibraryStatus

@Composable
internal fun StoryHero(
    story: StoryUiModel,
    libraryStatus: LibraryStatus?,
    readerTarget: ReaderTarget?,
    isResume: Boolean,
    downloadableReleaseId: ChapterReleaseId?,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onFindSource: () -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
    narrow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val artwork = rememberHikariArtwork(
        HikariArtworkModel(story.coverUrl, story.storyId.value, story.preferredTitle),
    )
    val heroHeight = if (narrow) {
        MaterialTheme.hikariDimensions.detailHeroNarrowHeight
    } else {
        MaterialTheme.hikariDimensions.detailHeroHeight
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        HikariArtworkBackdrop(artwork, Modifier.matchParentSize())
        if (narrow) {
            NarrowHeroContent(
                story = story,
                artwork = artwork,
                libraryStatus = libraryStatus,
                readerTarget = readerTarget,
                isResume = isResume,
                downloadableReleaseId = downloadableReleaseId,
                onLibraryStatusSelected = onLibraryStatusSelected,
                onRead = onRead,
                onFindSource = onFindSource,
                onDownload = onDownload,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        } else {
            WideHeroContent(
                story = story,
                artwork = artwork,
                libraryStatus = libraryStatus,
                readerTarget = readerTarget,
                isResume = isResume,
                downloadableReleaseId = downloadableReleaseId,
                onLibraryStatusSelected = onLibraryStatusSelected,
                onRead = onRead,
                onFindSource = onFindSource,
                onDownload = onDownload,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}
