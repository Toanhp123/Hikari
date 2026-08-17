package app.openstory.catalog.ui.download

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.control.HikariInlineAction
import app.openstory.designsystem.control.HikariInlineActionTone
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.feedback.HikariConfirmDialog
import app.openstory.designsystem.feedback.HikariConfirmationStyle
import app.openstory.designsystem.theme.hikariDimensions
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
    val actionModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
        .then(if (actionTag == null) Modifier else Modifier.testTag(actionTag))
    when (state) {
        DownloadState.QUEUED, DownloadState.RUNNING -> HikariUtilityAction(
            onClick = { actions.onCancel(releaseId) },
            modifier = modifier.then(actionModifier),
        ) { Text("Cancel") }
        DownloadState.FAILED, DownloadState.CANCELLED -> HikariUtilityAction(
            onClick = { actions.onRetry(releaseId) },
            modifier = modifier.then(actionModifier),
        ) { Text("Retry") }
        DownloadState.COMPLETED -> HikariInlineAction(
            onClick = { actions.onRemove(releaseId) },
            modifier = modifier.then(actionModifier),
            tone = HikariInlineActionTone.DESTRUCTIVE,
        ) { Text("Remove offline") }
        null -> HikariUtilityAction(
            onClick = { actions.onDownload(releaseId) },
            modifier = modifier.then(actionModifier),
        ) { Text("Download") }
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
