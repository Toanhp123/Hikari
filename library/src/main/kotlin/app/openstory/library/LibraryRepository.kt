package app.openstory.library

import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observe(): Flow<List<LibraryEntry>>

    /**
     * Adds membership if absent and otherwise returns the existing entry unchanged.
     */
    suspend fun add(
        storyId: StoryId,
        status: LibraryStatus,
        addedAt: Long,
    ): LibraryEntry

    suspend fun remove(storyId: StoryId)

    /**
     * Changes status for an existing membership and returns null when the story is not in Library.
     */
    suspend fun changeStatus(
        storyId: StoryId,
        status: LibraryStatus,
        updatedAt: Long,
    ): LibraryEntry?
}
