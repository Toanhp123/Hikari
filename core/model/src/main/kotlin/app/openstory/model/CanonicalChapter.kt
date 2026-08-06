package app.openstory.model

import java.math.BigDecimal

enum class ChapterKind {
    NUMBERED,
    PROLOGUE,
    EPILOGUE,
    SIDE_STORY,
    EXTRA,
    UNKNOWN,
}

data class CanonicalChapter(
    val id: ChapterId,
    val storyId: StoryId,
    val kind: ChapterKind,
    val volumeNumber: BigDecimal?,
    val chapterNumber: BigDecimal?,
    val partNumber: BigDecimal?,
    val normalizedTitle: String,
    val sortKey: String,
    val firstKnownPublishedAtEpochMillis: Long?,
) {
    init {
        require(normalizedTitle.isNotBlank()) {
            "Normalized chapter title must not be blank"
        }
        require(sortKey.isNotBlank()) {
            "Chapter sort key must not be blank"
        }
    }
}
