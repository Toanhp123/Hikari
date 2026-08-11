package app.openstory.library

import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LibraryService @Inject constructor(
    private val repository: LibraryRepository,
    private val clock: Clock,
    private val mappingScheduler: LibraryMappingScheduler,
) {
    fun observe(): Flow<List<LibraryEntry>> = repository.observe()

    suspend fun add(
        storyId: StoryId,
        status: LibraryStatus = LibraryStatus.WANT_TO_READ,
    ): LibraryEntry {
        val entry = repository.add(
            storyId = storyId,
            status = status,
            addedAt = clock.nowEpochMillis(),
        )
        try {
            mappingScheduler.schedule(storyId)
        } catch (_: RuntimeException) {
            // Membership is already committed; mapping work must never roll it back.
        }
        return entry
    }

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
