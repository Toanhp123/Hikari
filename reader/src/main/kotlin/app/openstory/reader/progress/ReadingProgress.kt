package app.openstory.reader.progress

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId

data class ReadingProgress(
    val storyId: StoryId,
    val canonicalChapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId,
    val contentFingerprint: String,
    val position: ReadingPosition,
    val completedAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(contentFingerprint.isNotBlank()) { "Content fingerprint must not be blank" }
        require(updatedAtEpochMillis >= 0) { "Updated time must not be negative" }
        require(completedAtEpochMillis == null || completedAtEpochMillis >= 0) {
            "Completion time must not be negative"
        }
    }
}

data class ReadingPosition(
    val blockId: String,
    val characterOffset: Int,
    val fraction: Float,
) {
    init {
        require(blockId.isNotBlank()) { "Block ID must not be blank" }
        require(characterOffset >= 0) { "Character offset must not be negative" }
        require(fraction.isFinite() && fraction in MIN_FRACTION..MAX_FRACTION) {
            "Progress fraction must be between zero and one"
        }
    }

    private companion object {
        const val MIN_FRACTION = 0f
        const val MAX_FRACTION = 1f
    }
}
