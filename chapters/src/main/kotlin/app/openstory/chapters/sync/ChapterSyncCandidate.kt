package app.openstory.chapters.sync

import app.openstory.common.id.StoryId

data class ChapterSyncCandidate(
    val storyId: StoryId,
    val lastSuccessfulSyncAtEpochMillis: Long?,
) {
    init {
        require(lastSuccessfulSyncAtEpochMillis == null || lastSuccessfulSyncAtEpochMillis >= 0L) {
            "Last successful sync time must not be negative"
        }
    }
}

data class ChapterSyncBatchCursor(
    val timestampBucket: Long?,
    val storyId: StoryId,
) {
    init {
        require(timestampBucket == null || timestampBucket >= 0L) {
            "Cursor timestamp must not be negative"
        }
    }
}

data class ChapterSyncBatch(
    val selected: List<ChapterSyncCandidate>,
    val continuation: ChapterSyncBatchCursor?,
)
