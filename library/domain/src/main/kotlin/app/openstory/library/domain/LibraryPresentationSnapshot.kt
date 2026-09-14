package app.openstory.library.domain

data class LibraryPresentationSnapshot(
    val title: String,
    val artwork: LibraryArtworkSnapshot?,
    val supportingText: String?,
) {
    init {
        requireBoundedText(title, MAX_LIBRARY_TITLE_CHARS, allowBlank = false)
        supportingText?.let { requireBoundedText(it, MAX_LIBRARY_SUPPORTING_TEXT_CHARS, allowBlank = false) }
    }
}

internal fun requireBoundedText(value: String, maxChars: Int, allowBlank: Boolean) {
    require(value.length <= maxChars)
    require(value.none(Char::isISOControl))
    if (!allowBlank) require(value.isNotBlank())
}

private const val MAX_LIBRARY_TITLE_CHARS = 512
private const val MAX_LIBRARY_SUPPORTING_TEXT_CHARS = 512
