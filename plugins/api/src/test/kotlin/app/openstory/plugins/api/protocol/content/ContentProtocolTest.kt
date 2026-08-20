package app.openstory.plugins.api.protocol.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ContentProtocolTest {
    @Test
    fun contentStoryRequestRejectsBlankSourceId() {
        assertFailsWith<IllegalArgumentException> { ContentStoryRequestDto(" ") }
    }

    @Test
    fun contentChapterRequestRejectsBlankReleaseId() {
        assertFailsWith<IllegalArgumentException> { ContentChapterRequestDto("") }
    }

    @Test
    fun imagePageRequiresStableIdentityAndHttpsDeliveryUrl() {
        val page = ImagePageBlockDto(
            stableId = "chapter-hash/page-001.png",
            imageUrl = "https://uploads.example.org/data/chapter/page-001.png",
        )

        assertEquals("chapter-hash/page-001.png", page.stableId)
        val encoded = Json.encodeToString(ChapterDocumentDto(null, listOf(page)))
        val decoded = Json.decodeFromString<ChapterDocumentDto>(encoded)
        assertIs<ImagePageBlockDto>(decoded.blocks.single())
        assertEquals(true, "\"type\":\"image\"" in encoded)
        assertFailsWith<IllegalArgumentException> {
            ImagePageBlockDto("page", "http://uploads.example.org/page.png")
        }
        assertFailsWith<IllegalArgumentException> {
            ImagePageBlockDto(" ", "https://uploads.example.org/page.png")
        }
    }

    @Test
    fun chapterProtocolExposesStructuredBlocksOnly() {
        val blocks: List<ChapterBlockDto> = listOf(
            HeadingBlockDto(level = 2, text = "Chapter"),
            ParagraphBlockDto("Body"),
            DividerBlockDto,
            NoteBlockDto("Note"),
        )
        ChapterDocumentDto(title = "One", blocks = blocks)
    }
}
