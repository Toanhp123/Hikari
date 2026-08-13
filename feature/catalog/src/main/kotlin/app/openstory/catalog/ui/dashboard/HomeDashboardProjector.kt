package app.openstory.catalog.ui.dashboard

import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.downloads.DownloadRecord
import app.openstory.downloads.DownloadState
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
    val downloads: List<DownloadRecord>,
)

class HomeDashboardProjector {
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
            return HomeDashboardItem(
                storyId = entry.storyId,
                title = projection?.title ?: entry.storyId.value,
                coverUrl = projection?.coverUrl,
                readerTarget = resumable?.let {
                    ReaderTarget(it.storyId, it.canonicalChapterId, it.releaseId)
                },
                progressFraction = resumable?.position?.fraction,
                chapterLabel = group?.chapter?.displayLabel,
                lastActivityAtEpochMillis = resumable?.updatedAtEpochMillis ?: entry.updatedAt,
            )
        }

        fun shelf(status: LibraryStatus) = library.filter { it.status == status }.map(::item)

        val continueReading = library.mapNotNull { entry ->
            latestProgress[entry.storyId]?.let { item(entry, it) }
        }.sortedWith(
            compareByDescending<HomeDashboardItem> { it.lastActivityAtEpochMillis }
                .thenBy { it.storyId.value },
        )

        val mappedKeys = input.mappings.mapTo(hashSetOf()) {
            Triple(it.storyId, it.pluginId, it.sourceStoryId)
        }
        val updates = input.chapters
            .flatMap { group -> group.releases.map { group.chapter.id to it } }
            .filter { (_, release) -> Triple(release.storyId, release.pluginId, release.sourceStoryId) in mappedKeys }
            .filter { (_, release) -> input.library.any { it.storyId == release.storyId } }
            .groupBy { (_, release) -> release.storyId }
            .mapNotNull { (storyId, releases) ->
                releases.maxWithOrNull(releaseOrder)?.let { (chapterId, release) ->
                    val projection = catalog[storyId]
                    HomeUpdateItem(
                        storyId = storyId,
                        title = projection?.title ?: storyId.value,
                        coverUrl = projection?.coverUrl,
                        chapterId = chapterId,
                        releaseId = release.id,
                        chapterLabel = release.displayLabel,
                        publishedAtEpochMillis = release.publishedAtEpochMillis,
                        readerTarget = ReaderTarget(storyId, chapterId, release.id),
                    )
                }
            }
            .sortedWith(
                compareByDescending<HomeUpdateItem> { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
                    .thenBy { it.releaseId.value },
            )

        return HomeDashboardUiState(
            summary = HomeReadingSummary(
                libraryCount = input.library.size,
                readingCount = input.library.count { it.status == LibraryStatus.READING },
                completedCount = input.library.count { it.status == LibraryStatus.COMPLETED },
                downloadedCount = input.downloads.count { it.state == DownloadState.COMPLETED },
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

private val releaseOrder = compareBy<Pair<app.openstory.common.id.CanonicalChapterId, ChapterRelease>> {
    it.second.publishedAtEpochMillis ?: Long.MIN_VALUE
}.thenBy { it.second.id.value }
