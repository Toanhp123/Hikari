package app.openstory.catalog.orchestration

import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.StoryIdentityInvariantException
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FakeDerivedDispatcher : PostMergeDerivedWorkDispatcher {
    val calls = mutableListOf<Pair<StoryId, PostMergeDerivedRequirements>>()
    var result: PostMergeDerivedWorkResult = PostMergeDerivedWorkResult.Dispatched

    override suspend fun dispatch(
        storyId: StoryId,
        requirements: PostMergeDerivedRequirements,
    ): PostMergeDerivedWorkResult {
        calls += storyId to requirements
        return result
    }
}

internal class FakeHealthMarker : CanonicalMaintenanceHealthMarker {
    val degraded = mutableListOf<StoryId>()

    override suspend fun markDegraded(storyId: StoryId) {
        degraded += storyId
    }
}

internal class FakeIdentity(
    private val invariantStoryIds: Set<StoryId> = emptySet(),
    private val missingStoryIds: Set<StoryId> = emptySet(),
) : StoryIdentityRepository {
    override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(storyId)

    override suspend fun resolve(storyId: StoryId): StoryId {
        if (storyId in invariantStoryIds) {
            throw StoryIdentityInvariantException("test identity invariant")
        }
        return storyId
    }

    override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? =
        if (storyId in missingStoryIds) {
            null
        } else {
            CanonicalIdentityState(storyId, identityRevision = 0L, createdAtEpochMillis = 0L)
        }
}
