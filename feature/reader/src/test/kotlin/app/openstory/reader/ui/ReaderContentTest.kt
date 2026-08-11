package app.openstory.reader.ui

import app.openstory.reader.document.ReaderBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderContentTest {
    private val blocks = listOf(
        ReaderBlock.Paragraph("first", "First"),
        ReaderBlock.Paragraph("second", "Second"),
    )

    @Test
    fun freshDocumentStartsAtTheTitle() {
        assertEquals(0, restoredReaderItemIndex(blocks, hasTitle = true, restoredBlockId = null))
    }

    @Test
    fun staleBlockIdentityFallsBackToTheTop() {
        assertEquals(0, restoredReaderItemIndex(blocks, hasTitle = true, restoredBlockId = "missing"))
    }

    @Test
    fun knownBlockIncludesTheTitleOffset() {
        assertEquals(2, restoredReaderItemIndex(blocks, hasTitle = true, restoredBlockId = "second"))
    }
}
