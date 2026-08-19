package app.openstory.catalog.ui.chapters

internal const val CHAPTER_PAGE_SIZE = 50

internal fun chapterPageCount(itemCount: Int): Int = when {
    itemCount <= 0 -> 1
    else -> ((itemCount - 1) / CHAPTER_PAGE_SIZE) + 1
}

internal fun <T> List<T>.chapterPage(page: Int): List<T> {
    if (isEmpty()) return emptyList()
    val safePage = page.coerceIn(1, chapterPageCount(size))
    val startIndex = (safePage - 1) * CHAPTER_PAGE_SIZE
    val endIndex = minOf(startIndex + CHAPTER_PAGE_SIZE, size)
    return subList(startIndex, endIndex)
}
