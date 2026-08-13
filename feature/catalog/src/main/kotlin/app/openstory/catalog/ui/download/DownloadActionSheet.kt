package app.openstory.catalog.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.feedback.HikariConfirmDialog
import app.openstory.designsystem.feedback.HikariConfirmationStyle
import app.openstory.downloads.DownloadState

@Composable
fun DownloadActionSheet(
    releaseId: ChapterReleaseId,
    state: DownloadState?,
    pendingRemoval: Boolean,
    actions: DownloadActions,
    modifier: Modifier = Modifier,
    actionTag: String? = null,
) {
    val actionModifier = Modifier.heightIn(min = 48.dp).then(
        if (actionTag == null) Modifier else Modifier.testTag(actionTag),
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (state) {
            DownloadState.QUEUED, DownloadState.RUNNING ->
                TextButton(onClick = { actions.onCancel(releaseId) }, modifier = actionModifier) { Text("Cancel") }
            DownloadState.FAILED, DownloadState.CANCELLED ->
                TextButton(onClick = { actions.onRetry(releaseId) }, modifier = actionModifier) { Text("Retry") }
            DownloadState.COMPLETED -> TextButton(onClick = { actions.onRemove(releaseId) }, modifier = actionModifier) { Text("Remove offline") }
            null -> TextButton(onClick = { actions.onDownload(releaseId) }, modifier = actionModifier) { Text("Download") }
        }
    }
    if (pendingRemoval) {
        HikariConfirmDialog(
            title = "Remove offline chapter?",
            message = "The chapter will need network access to open again.",
            confirmLabel = "Remove",
            dismissLabel = "Keep",
            onConfirm = actions.onConfirmRemoval,
            onDismiss = actions.onDismissRemoval,
            style = HikariConfirmationStyle.DESTRUCTIVE,
        )
    }
}

data class DownloadActions(
    val onDownload: (ChapterReleaseId) -> Unit = {},
    val onCancel: (ChapterReleaseId) -> Unit = {},
    val onRetry: (ChapterReleaseId) -> Unit = {},
    val onRemove: (ChapterReleaseId) -> Unit = {},
    val onConfirmRemoval: () -> Unit = {},
    val onDismissRemoval: () -> Unit = {},
)
