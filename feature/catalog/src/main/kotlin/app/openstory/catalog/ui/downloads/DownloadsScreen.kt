package app.openstory.catalog.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.catalog.ui.download.DownloadActionSheet
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.designsystem.content.HikariMetadataBadgeGroup
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariStickyDestinationScaffold
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.withScreenContentInsets
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
    val listState = rememberLazyListState()
    val headerScrolled = remember {
        derivedStateOf { listState.canScrollBackward }
    }
    HikariDestinationScaffold(modifier) {
        HikariStickyDestinationScaffold(
            contentPadding = contentPadding,
            header = { HikariTopLevelHeader(title = "Downloads") },
            headerScrolled = headerScrolled.value,
        ) { bodyPadding ->
            when {
                state.loading -> HikariLoadingState(
                    "Loading downloads",
                    Modifier.padding(bodyPadding),
                )
                state.isEmpty -> HikariEmptyState(
                    "No downloads yet",
                    modifier = Modifier.padding(bodyPadding),
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
                    bodyPadding,
                    listState,
                )
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
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding.withScreenContentInsets(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
    ) {
        val actions = DownloadListActions(
            onStorySelected,
            onRetry,
            onCancel,
            onRemove,
            onConfirmRemoval,
            onDismissRemoval,
        )
        listOf(
            "Active" to state.active,
            "Completed" to state.completed,
            "Failed" to state.failed,
        ).filter { (_, records) -> records.isNotEmpty() }
            .forEachIndexed { sectionIndex, (title, records) ->
                downloadSection(title, records, state, actions, separatedFromPrevious = sectionIndex > 0)
            }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.downloadSection(
    title: String,
    records: List<DownloadItemUiModel>,
    state: DownloadsUiState,
    actions: DownloadListActions,
    separatedFromPrevious: Boolean,
) {
    val firstRecord = records.first()
    item(key = "heading-$title") {
        HikariSectionLead(
            separatedFromPreviousSection = separatedFromPrevious,
            header = { HikariSectionHeader(title = title) },
            firstContent = {
                DownloadCard(firstRecord, state.pendingRemoval == firstRecord.releaseId, actions)
            },
        )
    }
    items(records.drop(1), key = { it.releaseId.value }) { item ->
        DownloadCard(item, state.pendingRemoval == item.releaseId, actions)
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItemUiModel,
    pendingRemoval: Boolean,
    actions: DownloadListActions,
) {
    HikariContentCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        item.storyId?.let { storyId ->
                            Modifier.clickable(role = Role.Button) { actions.onStorySelected(storyId) }
                        } ?: Modifier,
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            "${item.storyTitle}, ${item.chapterLabel}, ${item.state.name.lowercase()} download"
                    },
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            ) {
                Text(item.storyTitle, style = MaterialTheme.typography.titleMedium)
                Text(item.chapterLabel, style = MaterialTheme.typography.bodyMedium)
                HikariMetadataBadgeGroup(
                    listOfNotNull(
                        item.sourceLabel,
                        item.state.name.lowercase().replaceFirstChar(Char::uppercase),
                        item.sizeBytes.takeIf { it > 0L }?.byteLabel(),
                    ),
                )
                item.failureReason?.let { failure ->
                    HikariInlineFeedback(message = failure)
                }
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
