package app.openstory.storage.room.catalog

import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.StoryIdentityInvariantException
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomStoryIdentityResolver internal constructor(
    private val dao: CanonicalCatalogDao,
) : StoryIdentityRepository {
    constructor(database: OpenStoryDatabase) : this(database.canonicalCatalogDao())

    override fun observeResolved(storyId: StoryId): Flow<StoryId> = dao.observeRedirects()
        .map { redirects -> resolveFrom(storyId, redirects.associateBy(StoryRedirectEntity::retiredStoryId)) }
        .distinctUntilChanged()

    override suspend fun resolve(storyId: StoryId): StoryId =
        resolveFrom(storyId, dao.redirects().associateBy(StoryRedirectEntity::retiredStoryId))

    override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? {
        val resolved = resolve(storyId)
        val state = dao.canonicalState(resolved.value) ?: return null
        return CanonicalIdentityState(
            storyId = resolved,
            identityRevision = state.identityRevision,
            createdAtEpochMillis = state.createdAtEpochMillis,
        )
    }

    private fun resolveFrom(
        storyId: StoryId,
        redirects: Map<String, StoryRedirectEntity>,
    ): StoryId {
        var current = storyId.value
        val visited = linkedSetOf<String>()
        while (true) {
            if (!visited.add(current)) {
                throw StoryIdentityInvariantException("Story redirect cycle detected at $current")
            }
            val next = redirects[current]?.canonicalStoryId ?: return StoryId(current)
            current = next
        }
    }
}
