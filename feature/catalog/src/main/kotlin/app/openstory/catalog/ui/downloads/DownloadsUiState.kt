package app.openstory.catalog.ui.downloads

import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.downloads.DownloadState

data class DownloadItemUiModel(
    val releaseId: ChapterReleaseId,
    val storyId: StoryId?,
    val storyTitle: String,
    val chapterLabel: String,
    val sourceLabel: String?,
    val state: DownloadState,
    val sizeBytes: Long,
    val failureReason: String?,
    val updatedAtEpochMillis: Long,
)

data class DownloadsContent(
    val active: List<DownloadItemUiModel> = emptyList(),
    val completed: List<DownloadItemUiModel> = emptyList(),
    val failed: List<DownloadItemUiModel> = emptyList(),
) {
    val isEmpty: Boolean get() = active.isEmpty() && completed.isEmpty() && failed.isEmpty()
}

data class DownloadsUiState(
    val content: ContentState<DownloadsContent> = ContentState.Pending,
    val pendingRemoval: ChapterReleaseId? = null,
    val observationIssue: CatalogUiFailure? = null,
    val commandFailure: CatalogUiFailure? = null,
)
