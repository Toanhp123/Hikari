package app.openstory.catalog.ui.activity

import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.mapping.ContentMapping
import javax.inject.Inject

data class LibraryActivityItem(
    val storyId: StoryId,
    val title: String,
    val coverUrl: String?,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId,
    val chapterLabel: String,
    val sourceLabel: String,
    val languageTag: String,
    val publishedAtEpochMillis: Long?,
    val readerTarget: ReaderTarget?,
)

open class LibraryActivityProjector @Inject constructor() {
    open fun project(
        library: List<LibraryEntry>,
        catalog: List<CatalogStoryProjection>?,
        chapters: List<CanonicalChapterGroup>,
        mappings: List<ContentMapping>,
        readerPluginIds: Set<PluginId>?,
    ): List<LibraryActivityItem> {
        val libraryStories = library.mapTo(hashSetOf(), LibraryEntry::storyId)
        val catalogByStory = catalog.orEmpty().associateBy(CatalogStoryProjection::storyId)
        val mappedSources = mappings.mapTo(hashSetOf()) {
            Triple(it.storyId, it.pluginId, it.sourceStoryId)
        }
        return chapters
            .flatMap { group -> group.releases.map { release -> group.chapter.id to release } }
            .asSequence()
            .filter { (_, release) -> release.storyId in libraryStories }
            .filter { (_, release) ->
                Triple(release.storyId, release.pluginId, release.sourceStoryId) in mappedSources
            }
            .distinctBy { (_, release) -> release.id }
            .map { (chapterId, release) ->
                val projection = catalogByStory[release.storyId]
                LibraryActivityItem(
                    storyId = release.storyId,
                    title = projection?.title ?: release.storyId.value,
                    coverUrl = projection?.coverUrl,
                    chapterId = chapterId,
                    releaseId = release.id,
                    chapterLabel = release.displayLabel,
                    sourceLabel = release.pluginId.value,
                    languageTag = release.languageTag,
                    publishedAtEpochMillis = release.publishedAtEpochMillis,
                    readerTarget = readerPluginIds
                        ?.let { ids -> release.pluginId.takeIf(ids::contains) }
                        ?.let { ReaderTarget(release.storyId, chapterId, release.id) },
                )
            }
            .sortedWith(
                compareByDescending<LibraryActivityItem> { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
                    .thenBy { it.releaseId.value },
            )
            .toList()
    }
}
