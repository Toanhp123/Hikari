package app.openstory.catalog.ui.activity

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
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryActivityProjectorTest {
    private val projector = LibraryActivityProjector()

    @Test
    fun `keeps only mapped releases for library stories and sorts newest first`() {
        val libraryStory = StoryId("library-story")
        val outsideStory = StoryId("outside-story")

        val items = projector.project(
            library = listOf(entry(libraryStory)),
            catalog = listOf(projection(libraryStory, "Library Story")),
            chapters = listOf(
                group(libraryStory, "old", "mapped", 10L),
                group(libraryStory, "new", "mapped", 30L),
                group(libraryStory, "wrong-source", "unmapped", 40L),
                group(outsideStory, "outside", "outside", 50L),
            ),
            mappings = listOf(mapping(libraryStory, "mapped"), mapping(outsideStory, "outside")),
        )

        assertEquals(listOf("release-new", "release-old"), items.map { it.releaseId.value })
        assertEquals("Library Story", items.first().title)
        assertEquals("content.fixture", items.first().sourceLabel)
    }

    @Test
    fun `suppresses duplicate release identities and exposes valid navigation targets`() {
        val storyId = StoryId("story")
        val duplicate = group(storyId, "12", "mapped", 20L)

        val items = projector.project(
            library = listOf(entry(storyId)),
            catalog = emptyList(),
            chapters = listOf(duplicate, duplicate),
            mappings = listOf(mapping(storyId, "mapped")),
        )

        val item = items.single()
        assertEquals(storyId, item.storyId)
        assertEquals(storyId, item.readerTarget.storyId)
        assertEquals(item.chapterId, item.readerTarget.chapterId)
        assertEquals(item.releaseId, item.readerTarget.releaseId)
        assertEquals("story", item.title)
    }
}

private fun entry(storyId: StoryId) = LibraryEntry(storyId, LibraryStatus.READING, 1L, 2L)

private fun projection(storyId: StoryId, title: String) =
    CatalogStoryProjection(storyId, title, ContentType.WEB_NOVEL, null)

private fun mapping(storyId: StoryId, sourceStoryId: String) = ContentMapping(
    storyId, PluginId("content.fixture"), sourceStoryId, ContentMappingOrigin.AUTOMATED, 1, 1L,
)

private fun group(
    storyId: StoryId,
    suffix: String,
    sourceStoryId: String,
    publishedAt: Long,
): CanonicalChapterGroup {
    val chapterId = CanonicalChapterId("chapter-$suffix")
    val releaseId = ChapterReleaseId("release-$suffix")
    val parsed = ParsedChapterLabel(ChapterKind.NUMBERED, null, BigDecimal.ONE, null, null)
    val release = ChapterRelease(
        releaseId, storyId, PluginId("content.fixture"), sourceStoryId, "source-$suffix",
        "Chapter $suffix", parsed, "en", publishedAt, chapterId,
    )
    return CanonicalChapterGroup(
        CanonicalChapter(chapterId, storyId, parsed, "Chapter $suffix", false, setOf(releaseId)),
        listOf(release),
    )
}
