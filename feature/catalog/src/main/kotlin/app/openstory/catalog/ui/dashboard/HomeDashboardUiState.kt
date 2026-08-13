package app.openstory.catalog.ui.dashboard

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.catalog.ui.components.ReaderTarget

data class HomeReadingSummary(
    val libraryCount: Int = 0,
    val readingCount: Int = 0,
    val completedCount: Int = 0,
    val downloadedCount: Int = 0,
)

data class HomeDashboardItem(
    val storyId: StoryId,
    val title: String,
    val coverUrl: String?,
    val readerTarget: ReaderTarget? = null,
    val progressFraction: Float? = null,
    val chapterLabel: String? = null,
    val lastActivityAtEpochMillis: Long = 0L,
)

data class HomeUpdateItem(
    val storyId: StoryId,
    val title: String,
    val coverUrl: String?,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId,
    val chapterLabel: String,
    val publishedAtEpochMillis: Long?,
    val readerTarget: ReaderTarget,
)

data class HomeDashboardUiState(
    val summary: HomeReadingSummary = HomeReadingSummary(),
    val continueReading: List<HomeDashboardItem> = emptyList(),
    val reading: List<HomeDashboardItem> = emptyList(),
    val planned: List<HomeDashboardItem> = emptyList(),
    val paused: List<HomeDashboardItem> = emptyList(),
    val completed: List<HomeDashboardItem> = emptyList(),
    val latestUpdates: List<HomeUpdateItem> = emptyList(),
    val loading: Boolean = true,
    val failure: HomeDashboardFailure? = null,
) {
    val isEmpty: Boolean
        get() = continueReading.isEmpty() && reading.isEmpty() && planned.isEmpty() &&
            paused.isEmpty() && completed.isEmpty() && latestUpdates.isEmpty()
}

data class HomeDashboardFailure(val code: String, val retryable: Boolean)
