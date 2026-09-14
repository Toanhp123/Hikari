package app.openstory.library.domain

data class LibraryQuery(
    val text: String,
    val filter: LibraryFilter,
    val after: LibraryCursor?,
    val limit: Int = MAX_LIBRARY_WINDOW,
) {
    init {
        requireBoundedText(text, MAX_LIBRARY_QUERY_CHARS, allowBlank = true)
        require(limit in 1..MAX_LIBRARY_WINDOW)
    }

    val normalizedText: String
        get() = text.trim()
}

data class LibraryWindow(
    val items: List<LibraryEntry>,
    val nextCursor: LibraryCursor?,
) {
    init {
        require(items.size <= MAX_LIBRARY_WINDOW)
        require(items.distinctBy { it.ref.storyId }.size == items.size)
    }
}

const val MAX_LIBRARY_WINDOW = 60
private const val MAX_LIBRARY_QUERY_CHARS = 256
