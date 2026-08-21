package app.openstory.library.merge

import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry

data class LibraryMergePlan(val entry: LibraryEntry?)

class LibraryStoryMergePolicy {
    fun plan(
        survivorId: StoryId,
        left: LibraryEntry?,
        right: LibraryEntry?,
    ): LibraryMergePlan {
        val merged = when {
            left == null && right == null -> null
            left == null -> right?.copy(storyId = survivorId)
            right == null -> left.copy(storyId = survivorId)
            else -> mergeEntries(survivorId, left, right)
        }
        return LibraryMergePlan(merged)
    }

    private fun mergeEntries(
        survivorId: StoryId,
        left: LibraryEntry,
        right: LibraryEntry,
    ): LibraryEntry {
        val statusSource = when {
            left.updatedAt > right.updatedAt -> left
            right.updatedAt > left.updatedAt -> right
            left.storyId.value <= right.storyId.value -> left
            else -> right
        }
        return LibraryEntry(
            storyId = survivorId,
            status = statusSource.status,
            addedAt = minOf(left.addedAt, right.addedAt),
            updatedAt = maxOf(left.updatedAt, right.updatedAt),
        )
    }
}
