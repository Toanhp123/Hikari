package app.openstory.chapters.model

import java.math.BigDecimal

enum class ChapterKind {
    NUMBERED,
    PROLOGUE,
    EPILOGUE,
    SIDE_STORY,
    EXTRA,
    SPECIAL,
    UNKNOWN,
}

data class ParsedChapterLabel(
    val kind: ChapterKind,
    val volume: BigDecimal?,
    val chapter: BigDecimal?,
    val part: Int?,
    val normalizedTitle: String?,
)
