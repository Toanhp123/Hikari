package app.openstory.catalog.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.catalog.ui.download.DownloadActionSheet
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onStorySelected: (StoryId) -> Unit,
    onRetry: (ChapterReleaseId) -> Unit,
    onCancel: (ChapterReleaseId) -> Unit,
    onRemove: (ChapterReleaseId) -> Unit,
    onConfirmRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        HikariDestinationScaffold(modifier) {
            Column(Modifier.fillMaxSize().padding(contentPadding)) {
                HikariTopLevelHeader(title = "Downloads")
                when {
                    state.loading -> HikariLoadingState("Loading downloads")
                    state.isEmpty -> HikariEmptyState(
                        "No downloads yet",
                        message = "Downloaded chapters will appear here.",
                    )
                    else -> DownloadsList(
                        state,
                        onStorySelected,
                        onRetry,
                        onCancel,
                        onRemove,
                        onConfirmRemoval,
                        onDismissRemoval,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsList(
    state: DownloadsUiState,
    onStorySelected: (StoryId) -> Unit,
    onRetry: (ChapterReleaseId) -> Unit,
    onCancel: (ChapterReleaseId) -> Unit,
    onRemove: (ChapterReleaseId) -> Unit,
    onConfirmRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(MaterialTheme.hikariSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        val actions = DownloadListActions(
            onStorySelected,
            onRetry,
            onCancel,
            onRemove,
            onConfirmRemoval,
            onDismissRemoval,
        )
        downloadSection("Active", state.active, state, actions)
        downloadSection("Completed", state.completed, state, actions)
        downloadSection("Failed", state.failed, state, actions)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.downloadSection(
    title: String,
    records: List<DownloadItemUiModel>,
    state: DownloadsUiState,
    actions: DownloadListActions,
) {
    if (records.isEmpty()) return
    item(key = "heading-$title") {
        HikariSectionHeader(
            title = title,
            modifier = Modifier.padding(top = MaterialTheme.hikariSpacing.space8),
        )
    }
    items(records, key = { it.releaseId.value }) { item ->
        DownloadCard(item, state.pendingRemoval == item.releaseId, actions)
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItemUiModel,
    pendingRemoval: Boolean,
    actions: DownloadListActions,
) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "${item.storyTitle}, ${item.chapterLabel}, ${item.state.name.lowercase()} download"
        },
        onClick = { item.storyId?.let(actions.onStorySelected) },
        enabled = item.storyId != null,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space14),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text(item.storyTitle, style = MaterialTheme.typography.titleMedium)
            Text(item.chapterLabel, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space6)) {
                item.sourceLabel?.let { HikariMetadataBadge(it) }
                HikariMetadataBadge(item.state.name.lowercase().replaceFirstChar(Char::uppercase))
                item.sizeBytes.takeIf { it > 0L }?.let { HikariMetadataBadge(it.byteLabel()) }
            }
            item.failureReason?.let { failure ->
                HikariInlineFeedback(message = failure)
            }
            DownloadActionSheet(
                releaseId = item.releaseId,
                state = item.state,
                pendingRemoval = pendingRemoval,
                actions = DownloadActions(
                    onCancel = actions.onCancel,
                    onRetry = actions.onRetry,
                    onRemove = actions.onRemove,
                    onConfirmRemoval = actions.onConfirmRemoval,
                    onDismissRemoval = actions.onDismissRemoval,
                ),
            )
        }
    }
}

private fun Long.byteLabel(): String = when {
    this >= BYTES_PER_MEGABYTE -> "${this / BYTES_PER_MEGABYTE} MB"
    this >= BYTES_PER_KILOBYTE -> "${this / BYTES_PER_KILOBYTE} KB"
    else -> "$this B"
}

private data class DownloadListActions(
    val onStorySelected: (StoryId) -> Unit,
    val onRetry: (ChapterReleaseId) -> Unit,
    val onCancel: (ChapterReleaseId) -> Unit,
    val onRemove: (ChapterReleaseId) -> Unit,
    val onConfirmRemoval: () -> Unit,
    val onDismissRemoval: () -> Unit,
)

private const val BYTES_PER_KILOBYTE = 1L shl 10
private const val BYTES_PER_MEGABYTE = 1L shl 20
