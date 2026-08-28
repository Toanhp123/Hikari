package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.content.HikariSectionTitle
import app.openstory.designsystem.control.HikariIconAction
import app.openstory.designsystem.control.HikariPrimaryAction
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.icon.HikariMoreGlyph
import app.openstory.designsystem.layout.HikariModalSheet
import app.openstory.designsystem.layout.HikariSheetContent
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.LibraryStatus

@Composable
internal fun StoryHeroActions(
    libraryStatus: LibraryStatus?,
    primaryReadAction: StoryPrimaryReadAction,
    downloadableReleaseId: ChapterReleaseId?,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onFindSource: () -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryReadAction(
            action = primaryReadAction,
            onRead = onRead,
            onFindSource = onFindSource,
            modifier = Modifier.weight(1f),
        )
        HikariIconAction(
            onClick = { showActions = true },
            contentDescription = "More story actions",
            modifier = Modifier.testTag("story-more"),
        ) { HikariMoreGlyph() }
    }
    if (showActions) {
        StoryActionsSheet(
            libraryStatus = libraryStatus,
            downloadableReleaseId = downloadableReleaseId,
            onDismiss = { showActions = false },
            onLibraryStatusSelected = { status ->
                showActions = false
                onLibraryStatusSelected(status)
            },
            onDownload = { releaseId ->
                showActions = false
                onDownload(releaseId)
            },
        )
    }
}

@Composable
private fun StoryReadAction(
    action: StoryPrimaryReadAction,
    onRead: (ReaderTarget) -> Unit,
    onFindSource: () -> Unit,
    modifier: Modifier,
) {
    when (action) {
        StoryPrimaryReadAction.CheckingChapters -> DisabledStoryPrimaryAction(
            label = "Loading chapters",
            testTag = "story-chapters-checking",
            modifier = modifier,
        )
        StoryPrimaryReadAction.ChaptersUnavailable -> DisabledStoryPrimaryAction(
            label = "Chapters unavailable",
            testTag = "story-chapters-unavailable",
            modifier = modifier,
        )
        StoryPrimaryReadAction.NoReleases -> DisabledStoryPrimaryAction(
            label = "No releases available",
            testTag = "story-no-releases",
            modifier = modifier,
        )
        StoryPrimaryReadAction.CheckingSources -> DisabledStoryPrimaryAction(
            label = "Checking sources",
            testTag = "story-reader-checking",
            modifier = modifier,
        )
        StoryPrimaryReadAction.FindSource -> HikariPrimaryAction(
            onClick = onFindSource,
            modifier = modifier.testTag("story-find-source"),
        ) {
            Text("Find source", maxLines = 1)
        }
        is StoryPrimaryReadAction.Read -> HikariPrimaryAction(
            onClick = { onRead(action.target) },
            modifier = modifier.testTag("story-read"),
        ) {
            Text(if (action.isResume) "Resume" else "Read", maxLines = 1)
        }
    }
}

@Composable
private fun DisabledStoryPrimaryAction(
    label: String,
    testTag: String,
    modifier: Modifier,
) {
    HikariPrimaryAction(
        onClick = {},
        enabled = false,
        modifier = modifier.testTag(testTag),
    ) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun StoryActionsSheet(
    libraryStatus: LibraryStatus?,
    downloadableReleaseId: ChapterReleaseId?,
    onDismiss: () -> Unit,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
) {
    HikariModalSheet(onDismissRequest = onDismiss) {
        HikariSheetContent(title = "Story actions") {
            downloadableReleaseId?.let { releaseId ->
                HikariUtilityAction(
                    onClick = { onDownload(releaseId) },
                    modifier = Modifier.fillMaxWidth().testTag("story-download"),
                ) {
                    Text("Download", style = MaterialTheme.typography.titleMedium)
                }
            }
            HikariSectionTitle("Library")
            LibraryStatus.entries.forEach { status ->
                val selected = status == libraryStatus
                HikariUtilityAction(
                    onClick = { onLibraryStatusSelected(status) },
                    enabled = !selected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story-library-${status.name.lowercase()}"),
                ) {
                    Text(
                        if (selected) "${status.label()} · Current" else status.label(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (libraryStatus != null) {
                HikariUtilityAction(
                    onClick = { onLibraryStatusSelected(null) },
                    modifier = Modifier.fillMaxWidth().testTag("story-library-remove"),
                ) {
                    Text("Remove from Library", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun LibraryStatus.label() =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
