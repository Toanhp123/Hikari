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
    readerTarget: ReaderTarget?,
    isResume: Boolean,
    downloadableReleaseId: ChapterReleaseId?,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onRead: (ReaderTarget) -> Unit,
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
            readerTarget = readerTarget,
            isResume = isResume,
            onRead = onRead,
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
    readerTarget: ReaderTarget?,
    isResume: Boolean,
    onRead: (ReaderTarget) -> Unit,
    modifier: Modifier,
) {
    HikariPrimaryAction(
        onClick = { readerTarget?.let(onRead) },
        enabled = readerTarget != null,
        modifier = modifier.testTag("story-read"),
    ) {
        Text(if (isResume) "Resume" else "Read", maxLines = 1)
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
