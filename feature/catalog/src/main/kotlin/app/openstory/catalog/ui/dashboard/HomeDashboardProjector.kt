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
    val catalog: List<CatalogStoryProjection>? = null,
    val progress: List<ReadingProgress>? = null,
    val chapters: List<CanonicalChapterGroup>? = null,
    val mappings: List<ContentMapping>? = null,
    val readerPluginIds: Set<PluginId>? = null,
    val downloadedCount: Int? = null,
)

class HomeDashboardProjector(
    private val activityProjector: LibraryActivityProjector = LibraryActivityProjector(),
) {
    fun project(input: HomeDashboardInput): HomeDashboardContent {
        val catalog = input.catalog.orEmpty().associateBy(CatalogStoryProjection::storyId)
        val library = input.library.sortedWith(
            compareByDescending<LibraryEntry> { it.updatedAt }.thenBy { it.storyId.value },
        )
        val latestProgress = input.progress.orEmpty()
            .filter { it.completedAtEpochMillis == null }
            .groupBy { it.storyId }
            .mapValues { (_, records) ->
                records.maxWith(compareBy<ReadingProgress> { it.updatedAtEpochMillis }.thenBy { it.releaseId.value })
            }
        val groupsByChapter = input.chapters.orEmpty().associateBy { it.chapter.id }

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

        val updates = if (input.chapters != null && input.mappings != null) {
            activityProjector.project(
                input.library,
                input.catalog,
                input.chapters,
                input.mappings,
                input.readerPluginIds,
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
        } else {
            emptyList()
        }

        val reading = shelf(LibraryStatus.READING)
        val planned = shelf(LibraryStatus.WANT_TO_READ)
        val paused = shelf(LibraryStatus.PAUSED)
        val completed = shelf(LibraryStatus.COMPLETED)
        val noContentReason = when {
            input.library.isEmpty() -> HomeNoContentReason.NO_LIBRARY
            continueReading.isEmpty() && reading.isEmpty() && planned.isEmpty() &&
                paused.isEmpty() && completed.isEmpty() && updates.isEmpty() ->
                HomeNoContentReason.LIBRARY_PRESENT_BUT_NO_HOME_SECTIONS
            else -> null
        }

        return HomeDashboardContent(
            summary = HomeReadingSummary(
                libraryCount = input.library.size,
                readingCount = input.library.count { it.status == LibraryStatus.READING },
                completedCount = input.library.count { it.status == LibraryStatus.COMPLETED },
                downloadedCount = input.downloadedCount,
            ),
            continueReading = continueReading,
            reading = reading,
            planned = planned,
            paused = paused,
            completed = completed,
            latestUpdates = updates,
            noContentReason = noContentReason,
        )
    }
}
