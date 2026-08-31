package app.openstory.catalog.ui.dashboard

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryStatus
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeDashboardProjectorTest {
    private val projector = HomeDashboardProjector()

    @Test
    fun latestIncompleteProgressPerStoryBecomesContinueReading() {
        val content = project(
            library = listOf(entry("story-a", LibraryStatus.READING)),
            progress = listOf(progress("story-a", "1", 10L), progress("story-a", "2", 20L)),
            catalog = listOf(projection("story-a", "Alpha")),
            chapters = listOf(group(StoryId("story-a"), "2", "source-a", 20L)),
        )

        val item = content.continueReading.single()
        assertEquals("Alpha", item.title)
        assertEquals(CanonicalChapterId("chapter-2"), item.readerTarget?.chapterId)
        assertEquals(20L, item.lastActivityAtEpochMillis)
    }

    @Test
    fun existingProgressRemainsResumableWhenLiveSourceIsUnavailable() {
        val story = StoryId("story-a")
        val content = project(
            library = listOf(entry(story.value, LibraryStatus.READING)),
            progress = listOf(progress(story.value, "1", 10L)),
            chapters = listOf(group(story, "1", "source-a", 10L)),
            readerPluginIds = emptySet(),
        )

        assertEquals(ChapterReleaseId("release-1"), content.continueReading.single().readerTarget?.releaseId)
    }

    @Test
    fun completedProgressDoesNotBecomeContinueReading() {
        val content = project(
            library = listOf(entry("story-a", LibraryStatus.READING)),
            progress = listOf(progress("story-a", "1", 10L, completed = true)),
        )

        assertEquals(emptyList(), content.continueReading)
    }

    @Test
    fun libraryStatusCreatesReadingPlannedPausedCompletedShelves() {
        val content = project(
            library = listOf(
                entry("reading", LibraryStatus.READING),
                entry("planned", LibraryStatus.WANT_TO_READ),
                entry("paused", LibraryStatus.PAUSED),
                entry("completed", LibraryStatus.COMPLETED),
                entry("dropped", LibraryStatus.DROPPED),
            ),
        )

        assertEquals(listOf(StoryId("reading")), content.reading.map { it.storyId })
        assertEquals(listOf(StoryId("planned")), content.planned.map { it.storyId })
        assertEquals(listOf(StoryId("paused")), content.paused.map { it.storyId })
        assertEquals(listOf(StoryId("completed")), content.completed.map { it.storyId })
    }

    @Test
    fun latestMappedReleaseCreatesLibraryUpdate() {
        val story = StoryId("story-a")
        val content = project(
            library = listOf(entry(story.value, LibraryStatus.READING)),
            catalog = listOf(projection(story.value, "Alpha")),
            mappings = listOf(mapping(story, "source-a")),
            chapters = listOf(group(story, "1", "source-a", 10L), group(story, "2", "source-a", 20L)),
        )

        val update = content.latestUpdates.single()
        assertEquals("Alpha", update.title)
        assertEquals("Chapter 2", update.chapterLabel)
        assertEquals(ChapterReleaseId("release-2"), update.readerTarget?.releaseId)
    }


    @Test
    fun listOnlyLatestReleaseRemainsVisibleWithoutReaderTarget() {
        val story = StoryId("story-a")
        val content = project(
            library = listOf(entry(story.value, LibraryStatus.READING)),
            mappings = listOf(mapping(story, "source-a")),
            chapters = listOf(group(story, "1", "source-a", 10L)),
            readerPluginIds = emptySet(),
        )

        assertEquals(ChapterReleaseId("release-1"), content.latestUpdates.single().releaseId)
        assertNull(content.latestUpdates.single().readerTarget)
    }

    @Test
    fun missingCatalogProjectionKeepsStoryVisibleWithStableFallback() {
        val content = project(library = listOf(entry("story-orphan", LibraryStatus.WANT_TO_READ)))

        val item = content.planned.single()
        assertEquals("story-orphan", item.title)
        assertNull(item.coverUrl)
    }

    @Test
    fun nonEmptyLibraryRendersBaseShelvesBeforeOtherDependencies() {
        val content = project(
            library = listOf(entry("story-a", LibraryStatus.READING)),
        )

        assertEquals(listOf(StoryId("story-a")), content.reading.map { it.storyId })
        assertEquals("story-a", content.reading.single().title)
        assertNull(content.noContentReason)
    }

    @Test
    fun allDroppedLibraryNeverUsesNoLibraryReason() {
        val content = project(
            library = listOf(entry("story-a", LibraryStatus.DROPPED)),
        )

        assertEquals(HomeNoContentReason.LIBRARY_PRESENT_BUT_NO_HOME_SECTIONS, content.noContentReason)
        assertEquals(1, content.summary.libraryCount)
    }

    @Test
    fun missingDownloadCountIsUnknownNotZero() {
        val content = project()

        assertNull(content.summary.downloadedCount)
    }

    @Test
    fun unresolvedProgressDoesNotFabricateAnEmptyProgressFact() {
        val content = project(
            library = listOf(entry("story-a", LibraryStatus.READING)),
            progress = null,
        )

        assertEquals(emptyList(), content.continueReading)
        assertEquals(listOf(StoryId("story-a")), content.reading.map { it.storyId })
    }

    @Test
    fun chapterAndMappingArrivalAddsUpdatesBeforeReaderCapability() {
        val story = StoryId("story-a")
        val content = project(
            library = listOf(entry(story.value, LibraryStatus.READING)),
            chapters = listOf(group(story, "1", "source-a", 10L)),
            mappings = listOf(mapping(story, "source-a")),
            readerPluginIds = null,
        )

        assertEquals(ChapterReleaseId("release-1"), content.latestUpdates.single().releaseId)
        assertNull(content.latestUpdates.single().readerTarget)
    }

    private fun project(
        library: List<LibraryEntry> = emptyList(),
        catalog: List<CatalogStoryProjection>? = null,
        progress: List<ReadingProgress>? = null,
        chapters: List<CanonicalChapterGroup>? = null,
        mappings: List<ContentMapping>? = null,
        readerPluginIds: Set<PluginId>? = setOf(PluginId("content.a")),
        downloadedCount: Int? = null,
    ) = projector.project(
        HomeDashboardInput(
            library, catalog, progress, chapters, mappings,
            readerPluginIds = readerPluginIds,
            downloadedCount = downloadedCount,
        ),
    )
}

private fun entry(id: String, status: LibraryStatus) =
    LibraryEntry(StoryId(id), status, addedAt = 1L, updatedAt = 2L)

private fun projection(id: String, title: String) = CatalogStoryProjection(
    StoryId(id), title, ContentType.WEB_NOVEL, coverUrl = "https://example.test/$id.jpg",
)

private fun progress(id: String, chapter: String, updatedAt: Long, completed: Boolean = false) = ReadingProgress(
    storyId = StoryId(id),
    canonicalChapterId = CanonicalChapterId("chapter-$chapter"),
    releaseId = ChapterReleaseId("release-$chapter"),
    contentFingerprint = "fingerprint-$chapter",
    position = ReadingPosition("block", 0, 0.5f),
    completedAtEpochMillis = updatedAt.takeIf { completed },
    updatedAtEpochMillis = updatedAt,
)

private fun mapping(storyId: StoryId, sourceStoryId: String) = ContentMapping(
    storyId, PluginId("content.a"), sourceStoryId, ContentMappingOrigin.AUTOMATED, 1, 1L,
)

private fun group(storyId: StoryId, number: String, sourceStoryId: String, publishedAt: Long): CanonicalChapterGroup {
    val chapterId = CanonicalChapterId("chapter-$number")
    val releaseId = ChapterReleaseId("release-$number")
    val label = ParsedChapterLabel(ChapterKind.NUMBERED, null, BigDecimal(number), null, null)
    val release = ChapterRelease(
        releaseId, storyId, PluginId("content.a"), sourceStoryId, "source-release-$number",
        "Chapter $number", label, "en", publishedAt, chapterId,
    )
    return CanonicalChapterGroup(
        CanonicalChapter(chapterId, storyId, label, "Chapter $number", false, setOf(releaseId)),
        listOf(release),
    )
}
