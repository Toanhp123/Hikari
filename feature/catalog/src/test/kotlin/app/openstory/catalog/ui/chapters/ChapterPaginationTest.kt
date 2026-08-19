package app.openstory.catalog.ui.chapters

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterPaginationTest {
    @Test
    fun fiftyOneChaptersProduceTwoPages() {
        assertEquals(2, chapterPageCount(51))
    }

    @Test
    fun secondPageContainsOnlyRemainingChapters() {
        val chapters = (1..51).toList()

        assertEquals(listOf(51), chapters.chapterPage(2))
    }

    @Test
    fun pageSelectionClampsToAvailableRange() {
        val chapters = (1..51).toList()

        assertEquals((1..50).toList(), chapters.chapterPage(0))
        assertEquals(listOf(51), chapters.chapterPage(99))
    }
}
