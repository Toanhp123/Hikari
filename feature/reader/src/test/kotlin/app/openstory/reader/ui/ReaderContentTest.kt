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
    @Test
    fun visibleProgressBucketsToWholePercent() {
        assertEquals(0, fractionToPercent(0f))
        assertEquals(42, fractionToPercent(0.421f))
        assertEquals(42, fractionToPercent(0.429f))
        assertEquals(100, fractionToPercent(1f))
    }

}
