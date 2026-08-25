package app.openstory.chapters.sync

class ChapterSyncBatchPlanner(
    private val batchSize: Int = MAX_BATCH_SIZE,
) {
    init {
        require(batchSize in 1..MAX_BATCH_SIZE)
    }

    fun plan(
        candidates: List<ChapterSyncCandidate>,
        cursor: ChapterSyncBatchCursor? = null,
    ): ChapterSyncBatch {
        val ordered = candidates.distinctBy(ChapterSyncCandidate::storyId).sortedWith(candidateComparator)
        val remaining = if (cursor == null) {
            ordered
        } else {
            ordered.filter { candidate -> compare(candidate, cursor) > 0 }
        }
        val selected = remaining.take(batchSize)
        val continuation = selected.lastOrNull()
            ?.takeIf { remaining.size > selected.size }
            ?.let { ChapterSyncBatchCursor(it.lastSuccessfulSyncAtEpochMillis, it.storyId) }
        return ChapterSyncBatch(selected, continuation)
    }

    private fun compare(candidate: ChapterSyncCandidate, cursor: ChapterSyncBatchCursor): Int =
        candidateComparator.compare(
            candidate,
            ChapterSyncCandidate(cursor.storyId, cursor.timestampBucket),
        )

    companion object {
        const val MAX_BATCH_SIZE = 20

        private val candidateComparator = Comparator<ChapterSyncCandidate> { left, right ->
            when {
                left.lastSuccessfulSyncAtEpochMillis == null && right.lastSuccessfulSyncAtEpochMillis != null -> -1
                left.lastSuccessfulSyncAtEpochMillis != null && right.lastSuccessfulSyncAtEpochMillis == null -> 1
                else -> {
                    val timestamp = compareValues(
                        left.lastSuccessfulSyncAtEpochMillis,
                        right.lastSuccessfulSyncAtEpochMillis,
                    )
                    if (timestamp != 0) timestamp else left.storyId.value.compareTo(right.storyId.value)
                }
            }
        }
    }
}
