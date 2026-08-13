package app.openstory.catalog.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openstory.catalog.ui.download.DownloadActionSheet
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.downloads.DownloadState

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onStorySelected: (app.openstory.common.id.StoryId) -> Unit,
    onRetry: (app.openstory.common.id.ChapterReleaseId) -> Unit,
    onCancel: (app.openstory.common.id.ChapterReleaseId) -> Unit,
    onRemove: (app.openstory.common.id.ChapterReleaseId) -> Unit,
    onConfirmRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Text(
            "Downloads",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).semantics { heading() },
        )
        when {
            state.loading -> HikariLoadingState("Loading downloads")
            state.isEmpty -> HikariEmptyState("No downloads yet", message = "Downloaded chapters will appear here.")
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                downloadSection("Active", state.active, state, onStorySelected, onRetry, onCancel, onRemove, onConfirmRemoval, onDismissRemoval)
                downloadSection("Completed", state.completed, state, onStorySelected, onRetry, onCancel, onRemove, onConfirmRemoval, onDismissRemoval)
                downloadSection("Failed", state.failed, state, onStorySelected, onRetry, onCancel, onRemove, onConfirmRemoval, onDismissRemoval)
            }
        }
    }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.downloadSection(
    title: String,
    records: List<DownloadItemUiModel>,
    state: DownloadsUiState,
    onStorySelected: (app.openstory.common.id.StoryId) -> Unit,
    onRetry: (app.openstory.common.id.ChapterReleaseId) -> Unit,
    onCancel: (app.openstory.common.id.ChapterReleaseId) -> Unit,
    onRemove: (app.openstory.common.id.ChapterReleaseId) -> Unit,
    onConfirmRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
) {
    if (records.isEmpty()) return
    item(key = "heading-$title") {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
    }
    items(records, key = { it.releaseId.value }) { item ->
        DownloadCard(item, state.pendingRemoval == item.releaseId, onStorySelected, onRetry, onCancel, onRemove, onConfirmRemoval, onDismissRemoval)
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItemUiModel,
    pendingRemoval: Boolean,
    onStorySelected: (app.openstory.common.id.StoryId) -> Unit,
    onRetry: (app.openstory.common.id.ChapterReleaseId) -> Unit,
    onCancel: (app.openstory.common.id.ChapterReleaseId) -> Unit,
    onRemove: (app.openstory.common.id.ChapterReleaseId) -> Unit,
    onConfirmRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "${item.storyTitle}, ${item.chapterLabel}, ${item.state.name.lowercase()} download"
        },
        onClick = { item.storyId?.let(onStorySelected) },
        enabled = item.storyId != null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.storyTitle, style = MaterialTheme.typography.titleMedium)
            Text(item.chapterLabel, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.sourceLabel?.let { HikariMetadataBadge(it) }
                HikariMetadataBadge(item.state.name.lowercase().replaceFirstChar(Char::uppercase))
                item.sizeBytes.takeIf { it > 0L }?.let { HikariMetadataBadge(it.byteLabel()) }
            }
            item.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            DownloadActionSheet(
                releaseId = item.releaseId,
                state = item.state,
                pendingRemoval = pendingRemoval,
                actions = DownloadActions(
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onRemove = onRemove,
                    onConfirmRemoval = onConfirmRemoval,
                    onDismissRemoval = onDismissRemoval,
                ),
            )
        }
    }
}

private fun Long.byteLabel(): String = when {
    this >= 1024L * 1024L -> "${this / (1024L * 1024L)} MB"
    this >= 1024L -> "${this / 1024L} KB"
    else -> "$this B"
}
