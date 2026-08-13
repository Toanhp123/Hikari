package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkBackdrop
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.rememberHikariArtwork
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
    onDownload: (ChapterReleaseId) -> Unit,
    narrow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val artwork = rememberHikariArtwork(HikariArtworkModel(story.coverUrl, story.storyId.value, story.preferredTitle))
    Box(modifier.fillMaxWidth().height(if (narrow) 380.dp else 304.dp)) {
        HikariArtworkBackdrop(artwork, Modifier.matchParentSize())
        if (narrow) {
            NarrowHeroContent(
                story, artwork, libraryStatus, readerTarget, isResume, downloadableReleaseId,
                onLibraryStatusSelected, onRead, onDownload,
                Modifier.align(Alignment.BottomStart),
            )
        } else {
            WideHeroContent(
                story, artwork, libraryStatus, readerTarget, isResume, downloadableReleaseId,
                onLibraryStatusSelected, onRead, onDownload,
                Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun WideHeroContent(
    story: StoryUiModel,
    artwork: app.openstory.designsystem.artwork.HikariArtworkState,
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
        modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
            HikariArtwork(
                artwork,
                "${story.preferredTitle} cover",
                Modifier.size(width = 112.dp, height = 164.dp).clip(MaterialTheme.shapes.medium),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    story.preferredTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    story.metadataLabel(),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { readerTarget?.let(onRead) },
                        enabled = readerTarget != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("story-read"),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text(if (isResume) "Resume" else "Read") }
                    if (downloadableReleaseId != null) {
                        OutlinedButton(
                            onClick = { onDownload(downloadableReleaseId) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("story-download"),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Download") }
                    }
                }
                LibraryStatusMenu(libraryStatus, onLibraryStatusSelected)
            }
    }
}

@Composable
private fun NarrowHeroContent(
    story: StoryUiModel,
    artwork: app.openstory.designsystem.artwork.HikariArtworkState,
    libraryStatus: LibraryStatus?,
    readerTarget: ReaderTarget?,
    isResume: Boolean,
    downloadableReleaseId: ChapterReleaseId?,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            HikariArtwork(
                artwork, "${story.preferredTitle} cover",
                Modifier.size(width = 88.dp, height = 128.dp).clip(MaterialTheme.shapes.medium),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    story.preferredTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 3,
                )
                Text(
                    story.metadataLabel(),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { readerTarget?.let(onRead) }, enabled = readerTarget != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("story-read"),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text(if (isResume) "Resume" else "Read") }
            if (downloadableReleaseId != null) {
                OutlinedButton(
                    onClick = { onDownload(downloadableReleaseId) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("story-download"),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Download") }
            }
        }
        LibraryStatusMenu(libraryStatus, onLibraryStatusSelected)
    }
}

@Composable
private fun LibraryStatusMenu(status: LibraryStatus?, onSelected: (LibraryStatus?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("story-library"),
        ) {
            Text(status?.label() ?: "Add to Library")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibraryStatus.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label()) },
                    onClick = { expanded = false; onSelected(item) },
                )
            }
            if (status != null) {
                DropdownMenuItem(
                    text = { Text("Remove from Library") },
                    onClick = { expanded = false; onSelected(null) },
                )
            }
        }
    }
}

private fun LibraryStatus.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun ContentType.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun StoryUiModel.metadataLabel(): String =
    listOfNotNull(contentType.label(), score?.let { "${it.value}/${it.scale}" }).joinToString(" · ")
