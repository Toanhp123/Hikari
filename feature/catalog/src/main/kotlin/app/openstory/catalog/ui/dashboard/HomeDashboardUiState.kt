package app.openstory.catalog.ui.dashboard

import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId

enum class HomeNoContentReason {
    NO_LIBRARY,
    LIBRARY_PRESENT_BUT_NO_HOME_SECTIONS,
}

data class HomeReadingSummary(
    val libraryCount: Int = 0,
    val readingCount: Int = 0,
    val completedCount: Int = 0,
    val downloadedCount: Int? = null,
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
    val readerTarget: ReaderTarget?,
)

data class HomeDashboardContent(
    val summary: HomeReadingSummary = HomeReadingSummary(),
    val continueReading: List<HomeDashboardItem> = emptyList(),
    val reading: List<HomeDashboardItem> = emptyList(),
    val planned: List<HomeDashboardItem> = emptyList(),
    val paused: List<HomeDashboardItem> = emptyList(),
    val completed: List<HomeDashboardItem> = emptyList(),
    val latestUpdates: List<HomeUpdateItem> = emptyList(),
    val noContentReason: HomeNoContentReason? = null,
)

data class HomeDashboardUiState(
    val content: ContentState<HomeDashboardContent> = ContentState.Pending,
    val observationIssue: CatalogUiFailure? = null,
)
