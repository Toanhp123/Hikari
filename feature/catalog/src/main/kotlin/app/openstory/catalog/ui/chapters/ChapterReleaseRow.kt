package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.catalog.ui.download.DownloadActionSheet
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.downloads.DownloadState

@Composable
fun ChapterReleaseRow(
    release: ChapterReleaseUiModel,
    chapterId: CanonicalChapterId,
    onKeepGrouped: (ChapterReleaseId, CanonicalChapterId) -> Unit,
    onSeparate: (ChapterReleaseId) -> Unit,
    onRead: (CanonicalChapterId, ChapterReleaseId) -> Unit,
    downloadState: DownloadState?,
    pendingRemoval: Boolean,
    downloadActions: DownloadActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("${release.sourceName} · ${release.languageLabel}")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onKeepGrouped(release.id, chapterId) }) {
                Text("Keep grouped")
            }
            TextButton(onClick = { onSeparate(release.id) }) {
                Text("Separate")
            }
            TextButton(onClick = { onRead(chapterId, release.id) }) {
                Text("Read")
            }
        }
        DownloadActionSheet(release.id, downloadState, pendingRemoval, downloadActions)
    }
}
