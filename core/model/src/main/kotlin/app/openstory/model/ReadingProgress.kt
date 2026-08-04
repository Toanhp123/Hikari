package app.openstory.model

sealed interface ReaderPosition {

    data class Paragraph(
        val index: Int,
        val fraction: Float,
    ) : ReaderPosition {
        init {
            require(index >= 0) {
                "Paragraph index must not be negative"
            }
            require(fraction in 0f..1f) {
                "Paragraph fraction must be between 0 and 1"
            }
        }
    }

    data object Start : ReaderPosition
}

data class ReadingProgress(
    val storyId: StoryId,
    val chapterId: ChapterId,
    val releaseId: ReleaseId?,
    val position: ReaderPosition,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
)
