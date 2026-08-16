package app.openstory.reader.document

import app.openstory.plugins.api.protocol.content.ChapterDocumentDto
import app.openstory.plugins.api.protocol.content.DividerBlockDto
import app.openstory.plugins.api.protocol.content.HeadingBlockDto
import app.openstory.plugins.api.protocol.content.ParagraphBlockDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderDocumentSanitizerTest {
    private val sanitizer = ReaderDocumentSanitizer()

    @Test
    fun rejectsEmptyDocument() {
        assertInvalid("reader.document_empty", ChapterDocumentDto(null, emptyList()))
    }

    @Test
    fun rejectsControlCharactersAndOversizedParagraphs() {
        assertInvalid("reader.document_title_invalid", ChapterDocumentDto("bad\u0000title", listOf(DividerBlockDto)))
        assertInvalid(
            "reader.document_block_invalid",
            ChapterDocumentDto(null, listOf(ParagraphBlockDto("a".repeat(50_001)))),
        )
    }

    @Test
    fun preservesCanonicalFingerprintAndBlockIds() {
        val input = ChapterDocumentDto(
            " Chapter one ",
            listOf(HeadingBlockDto(2, "Arrival"), ParagraphBlockDto(" First paragraph "), DividerBlockDto),
        )

        val document = assertIs<DocumentValidationResult.Valid>(sanitizer.sanitize(input)).document

        assertEquals("block-0-794adbbc6c1c", document.blocks[0].id)
        assertEquals("block-1-4b3bab2d4d4b", document.blocks[1].id)
        assertEquals("block-2-002cd6bbb3f7", document.blocks[2].id)
        assertEquals("4d9d5066c6a1816410311c8341572891ad20e297f895a76215c19261e10a422c", document.fingerprint)
        assertTrue(document.fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun createsStableBoundedStructuredDocument() {
        val input = ChapterDocumentDto(
            " Chapter one ",
            listOf(HeadingBlockDto(2, "Arrival"), ParagraphBlockDto(" First paragraph "), DividerBlockDto),
        )
        val first = assertIs<DocumentValidationResult.Valid>(sanitizer.sanitize(input)).document
        val second = assertIs<DocumentValidationResult.Valid>(sanitizer.sanitize(input)).document

        assertEquals(first, second)
        assertEquals("Chapter one", first.title)
        assertEquals("First paragraph", (first.blocks[1] as ReaderBlock.Paragraph).text)
        assertEquals(64, first.fingerprint.length)
    }

    private fun assertInvalid(code: String, input: ChapterDocumentDto) {
        assertEquals(code, assertIs<DocumentValidationResult.Invalid>(sanitizer.sanitize(input)).code)
    }
}
