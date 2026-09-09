package app.openstory.catalog.storage.retention

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.common.id.StoryId

internal class StoryRetentionStorage(
    private val dao: StoryRetentionDao,
) {
    suspend fun removeNewlyReachable(storyIds: Set<StoryId>) {
        if (storyIds.isNotEmpty()) dao.removeOrphans(storyIds.mapTo(linkedSetOf(), StoryId::value))
    }

    suspend fun classify(
        storyId: StoryId,
        identityExists: Boolean,
        protectedStoryIds: Set<StoryId>,
        lastAccessedEpochMs: Long,
    ) {
        val value = storyId.value
        when {
            !identityExists -> dao.removeOrphan(value)
            dao.isReachableFromAnyCurrentDiscover(value) -> dao.removeOrphan(value)
            storyId in protectedStoryIds || dao.hasDetail(value) -> dao.touchOrphan(value, lastAccessedEpochMs)
            else -> {
                dao.removeOrphan(value)
                dao.deleteUnreachableStories(setOf(value))
            }
        }
    }

    suspend fun evictOneOverflow(protectedStoryIds: Set<StoryId>): StoryId? {
        val oldest = dao.oldestOrphans()
        val protected = protectedStoryIds.mapTo(linkedSetOf(), StoryId::value)
        val eviction = when {
            oldest.size <= RETENTION_LIMIT -> null
            protected.isEmpty() -> oldest.first()
            else -> dao.oldestOrphanExcluding(protected)
        }
        if (eviction != null) {
            dao.removeOrphan(eviction.storyId)
            dao.deleteUnreachableStories(setOf(eviction.storyId))
        }
        return eviction?.let { StoryId(it.storyId) }
    }

    suspend fun requireWithinLimit() {
        if (dao.oldestOrphans().size > RETENTION_LIMIT) {
            throw CatalogFailureException(CatalogFailure.InternalInvariant("orphan_retention_overflow"))
        }
    }

    companion object {
        const val RETENTION_LIMIT = 64
    }
}
