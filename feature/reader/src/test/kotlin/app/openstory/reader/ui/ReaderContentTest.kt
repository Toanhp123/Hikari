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
    fun imagePagesDoNotInventCharacterOffsets() {
        val page = ReaderBlock.ImagePage("page", "hash/page.png", "https://node.example/page.png")

        assertEquals(0, page.progressExtent())
        assertEquals(5, ReaderBlock.Paragraph("text", "Hello").progressExtent())
    }

    @Test
    fun imageRestoreUsesDocumentFractionWithinTheKnownPage() {
        assertEquals(0.5f, restoredImagePageFraction(blockIndex = 1, blockCount = 4, documentFraction = 0.375f))
        assertEquals(0f, restoredImagePageFraction(blockIndex = 2, blockCount = 4, documentFraction = 0.1f))
        assertEquals(1f, restoredImagePageFraction(blockIndex = 2, blockCount = 4, documentFraction = 0.9f))
    }

    @Test
    fun readerProgressSamplingIsBoundedToTenUpdatesPerSecond() {
        assertEquals(100L, READER_PROGRESS_SAMPLE_MILLIS)
    }

    @Test
    fun visibleProgressBucketsToWholePercent() {
        assertEquals(0, fractionToPercent(0f))
        assertEquals(42, fractionToPercent(0.421f))
        assertEquals(42, fractionToPercent(0.429f))
        assertEquals(100, fractionToPercent(1f))
    }
}
