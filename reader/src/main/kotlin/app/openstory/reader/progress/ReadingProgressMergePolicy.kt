package app.openstory.reader.progress

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.StoryId
import app.openstory.common.merge.DomainMergeDecision

const val READING_PROGRESS_UNSAFE_CONFLICT = "reading_progress.unsafe_conflict"
const val READING_PROGRESS_REVERSAL_STATE_CHANGED = "reading_progress.reversal_state_changed"

data class ReadingProgressMergePlan(
    val progressRows: List<ReadingProgress>,
)

class ReadingProgressMergePolicy {
    fun reversalBlockers(
        survivorStoryId: StoryId,
        current: List<ReadingProgress>,
        survivorBefore: List<ReadingProgress>,
        retiredBefore: List<ReadingProgress>,
    ): Set<String> {
        val expected = when (val decision = plan(survivorStoryId, survivorBefore, retiredBefore)) {
            is DomainMergeDecision.Ready -> decision.value.progressRows.toSet()
            is DomainMergeDecision.RequiresReview -> null
        }
        return if (expected != null && current.toSet() == expected) {
            emptySet()
        } else {
            setOf(READING_PROGRESS_REVERSAL_STATE_CHANGED)
        }
    }

    fun plan(
        survivorStoryId: StoryId,
        left: List<ReadingProgress>,
        right: List<ReadingProgress>,
    ): DomainMergeDecision<ReadingProgressMergePlan> {
        val merged = mutableListOf<ReadingProgress>()
        val byChapter = (left + right).groupBy(ReadingProgress::canonicalChapterId)
        byChapter.entries
            .sortedBy { it.key.value }
            .forEach { (_, rows) ->
                val selected = mergeRows(rows) ?: return DomainMergeDecision.RequiresReview(
                    setOf(READING_PROGRESS_UNSAFE_CONFLICT),
                )
                merged += selected.copy(storyId = survivorStoryId)
            }
        return DomainMergeDecision.Ready(ReadingProgressMergePlan(merged))
    }

    private fun mergeRows(rows: List<ReadingProgress>): ReadingProgress? {
        val ordered = rows.sortedWith(stableOrder)
        var selected = ordered.firstOrNull()
        for (candidate in ordered.drop(1)) {
            selected = selected?.let { mergePair(it, candidate) }
        }
        return selected
    }

    private fun mergePair(left: ReadingProgress, right: ReadingProgress): ReadingProgress? {
        require(left.canonicalChapterId == right.canonicalChapterId)
        return when {
            left.contentFingerprint == right.contentFingerprint -> furthestSameContent(left, right)
            left.releaseId != right.releaseId -> null
            left.completedAtEpochMillis != null || right.completedAtEpochMillis != null ->
                furthestCompletion(left, right)
            left.position.blockId == right.position.blockId -> furthestStableBlock(left, right)
            else -> null
        }
    }

    private fun furthestSameContent(left: ReadingProgress, right: ReadingProgress): ReadingProgress {
        val leftCompleted = left.completedAtEpochMillis != null
        val rightCompleted = right.completedAtEpochMillis != null
        val fractionOrder = left.position.fraction.compareTo(right.position.fraction)
        return when {
            leftCompleted != rightCompleted -> if (leftCompleted) left else right
            fractionOrder != 0 -> if (fractionOrder > 0) left else right
            else -> latestStable(left, right)
        }
    }

    private fun furthestCompletion(left: ReadingProgress, right: ReadingProgress): ReadingProgress {
        val leftCompleted = left.completedAtEpochMillis != null
        val rightCompleted = right.completedAtEpochMillis != null
        return when {
            leftCompleted && !rightCompleted -> left
            rightCompleted && !leftCompleted -> right
            leftCompleted && rightCompleted -> latestStable(left, right)
            else -> error("At least one progress row must be completed")
        }
    }

    private fun furthestStableBlock(left: ReadingProgress, right: ReadingProgress): ReadingProgress {
        val offsetOrder = left.position.characterOffset.compareTo(right.position.characterOffset)
        if (offsetOrder != 0) return if (offsetOrder > 0) left else right
        return latestStable(left, right)
    }

    private fun latestStable(left: ReadingProgress, right: ReadingProgress): ReadingProgress {
        val updateOrder = left.updatedAtEpochMillis.compareTo(right.updatedAtEpochMillis)
        if (updateOrder != 0) return if (updateOrder > 0) left else right
        return if (stableOrder.compare(left, right) <= 0) left else right
    }

    private companion object {
        val stableOrder = compareBy<ReadingProgress> { it.canonicalChapterId.value }
            .thenBy { it.releaseId.value }
            .thenBy { it.contentFingerprint }
            .thenBy { it.position.blockId }
            .thenBy { it.position.characterOffset }
            .thenBy { it.position.fraction }
            .thenBy { it.completedAtEpochMillis ?: Long.MAX_VALUE }
            .thenBy { it.updatedAtEpochMillis }
            .thenBy { it.storyId.value }
    }
}
