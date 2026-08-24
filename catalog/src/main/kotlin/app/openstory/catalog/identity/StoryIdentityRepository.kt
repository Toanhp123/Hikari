package app.openstory.catalog.identity

import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow

data class CanonicalIdentityState(
    val storyId: StoryId,
    val identityRevision: Long,
    val createdAtEpochMillis: Long?,
) {
    init {
        require(identityRevision >= 0L)
        require(createdAtEpochMillis == null || createdAtEpochMillis >= 0L)
    }
}

class StoryIdentityInvariantException(message: String) : IllegalStateException(message)

interface StoryIdentityRepository {
    fun observeResolved(storyId: StoryId): Flow<StoryId>
    suspend fun resolve(storyId: StoryId): StoryId
    suspend fun resolveAll(storyIds: Collection<StoryId>): Map<StoryId, StoryId> =
        storyIds.distinct().associateWith { storyId -> resolve(storyId) }
    suspend fun identityState(storyId: StoryId): CanonicalIdentityState?
}
