package app.openstory.catalog.ui.dashboard

import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.activity.LibraryActivityProjector
import app.openstory.common.id.PluginId
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryStatus
import app.openstory.library.mapping.ContentMapping
import app.openstory.reader.progress.ReadingProgress

data class HomeDashboardInput(
    val library: List<LibraryEntry>,
    val catalog: List<CatalogStoryProjection>,
    val progress: List<ReadingProgress>,
    val chapters: List<CanonicalChapterGroup>,
    val mappings: List<ContentMapping>,
    val readerPluginIds: Set<PluginId>,
    val downloadedCount: Int,
)

class HomeDashboardProjector(
    private val activityProjector: LibraryActivityProjector = LibraryActivityProjector(),
) {
    fun project(input: HomeDashboardInput): HomeDashboardUiState {
        val catalog = input.catalog.associateBy(CatalogStoryProjection::storyId)
        val library = input.library.sortedWith(
            compareByDescending<LibraryEntry> { it.updatedAt }.thenBy { it.storyId.value },
        )
        val latestProgress = input.progress
            .filter { it.completedAtEpochMillis == null }
            .groupBy { it.storyId }
            .mapValues { (_, records) ->
                records.maxWith(compareBy<ReadingProgress> { it.updatedAtEpochMillis }.thenBy { it.releaseId.value })
            }
        val groupsByChapter = input.chapters.associateBy { it.chapter.id }

        fun item(entry: LibraryEntry, resumable: ReadingProgress? = null): HomeDashboardItem {
            val projection = catalog[entry.storyId]
            val group = resumable?.let { groupsByChapter[it.canonicalChapterId] }
            val readerTarget = resumable?.let { progress ->
                ReaderTarget(progress.storyId, progress.canonicalChapterId, progress.releaseId)
            }
            return HomeDashboardItem(
                storyId = entry.storyId,
                title = projection?.title ?: entry.storyId.value,
                coverUrl = projection?.coverUrl,
                readerTarget = readerTarget,
                progressFraction = resumable?.position?.fraction,
                chapterLabel = group?.chapter?.displayLabel,
                lastActivityAtEpochMillis = resumable?.updatedAtEpochMillis ?: entry.updatedAt,
            )
        }

        fun shelf(status: LibraryStatus) = library.filter { it.status == status }.map(::item)

        val continueReading = library.mapNotNull { entry ->
            latestProgress[entry.storyId]?.let { progress -> item(entry, progress) }
        }.sortedWith(
            compareByDescending<HomeDashboardItem> { it.lastActivityAtEpochMillis }
                .thenBy { it.storyId.value },
        )

        val updates = activityProjector.project(
            input.library, input.catalog, input.chapters, input.mappings, input.readerPluginIds,
        )
            .distinctBy { it.storyId }
            .map { activity ->
                HomeUpdateItem(
                    storyId = activity.storyId,
                    title = activity.title,
                    coverUrl = activity.coverUrl,
                    chapterId = activity.chapterId,
                    releaseId = activity.releaseId,
                    chapterLabel = activity.chapterLabel,
                    publishedAtEpochMillis = activity.publishedAtEpochMillis,
                    readerTarget = activity.readerTarget,
                )
            }

        return HomeDashboardUiState(
            summary = HomeReadingSummary(
                libraryCount = input.library.size,
                readingCount = input.library.count { it.status == LibraryStatus.READING },
                completedCount = input.library.count { it.status == LibraryStatus.COMPLETED },
                downloadedCount = input.downloadedCount,
            ),
            continueReading = continueReading,
            reading = shelf(LibraryStatus.READING),
            planned = shelf(LibraryStatus.WANT_TO_READ),
            paused = shelf(LibraryStatus.PAUSED),
            completed = shelf(LibraryStatus.COMPLETED),
            latestUpdates = updates,
            loading = false,
        )
    }
}
