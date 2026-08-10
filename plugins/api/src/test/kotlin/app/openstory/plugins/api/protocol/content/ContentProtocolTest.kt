package app.openstory.plugins.api.protocol.content

import kotlin.test.Test
import kotlin.test.assertFailsWith

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
