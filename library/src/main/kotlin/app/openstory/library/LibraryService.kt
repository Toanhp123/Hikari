package app.openstory.library

import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LibraryService @Inject constructor(
    private val repository: LibraryRepository,
    private val clock: Clock,
) {
    fun observe(): Flow<List<LibraryEntry>> = repository.observe()

    suspend fun add(
        storyId: StoryId,
        status: LibraryStatus = LibraryStatus.WANT_TO_READ,
    ): LibraryEntry = repository.add(
        storyId = storyId,
        status = status,
        addedAt = clock.nowEpochMillis(),
    )

    suspend fun remove(storyId: StoryId) {
        repository.remove(storyId)
    }

    suspend fun changeStatus(
        storyId: StoryId,
        status: LibraryStatus,
    ): LibraryEntry? = repository.changeStatus(
        storyId = storyId,
        status = status,
        updatedAt = clock.nowEpochMillis(),
    )
}
