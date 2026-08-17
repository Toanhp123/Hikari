package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.control.HikariPrimaryAction
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.menu.HikariDropdownMenu
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.LibraryStatus

@Composable
internal fun WideStoryHeroActions(
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
        StoryReadAction(
            readerTarget = readerTarget,
            isResume = isResume,
            onRead = onRead,
            modifier = Modifier.weight(1f),
        )
        downloadableReleaseId?.let { releaseId ->
            StoryDownloadAction(
                releaseId = releaseId,
                onDownload = onDownload,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun NarrowStoryHeroActions(
    readerTarget: ReaderTarget?,
    isResume: Boolean,
    downloadableReleaseId: ChapterReleaseId?,
    onRead: (ReaderTarget) -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        StoryReadAction(
            readerTarget = readerTarget,
            isResume = isResume,
            onRead = onRead,
            modifier = Modifier.fillMaxWidth(),
        )
        downloadableReleaseId?.let { releaseId ->
            StoryDownloadAction(
                releaseId = releaseId,
                onDownload = onDownload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
private fun StoryDownloadAction(
    releaseId: ChapterReleaseId,
    onDownload: (ChapterReleaseId) -> Unit,
    modifier: Modifier,
) {
    HikariUtilityAction(
        onClick = { onDownload(releaseId) },
        modifier = modifier.testTag("story-download"),
    ) {
        Text("Download", maxLines = 1)
    }
}

@Composable
internal fun LibraryStatusMenu(
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
