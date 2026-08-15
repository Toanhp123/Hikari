package app.openstory.catalog.projection

import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface CatalogStoryProjectionRepository {
    fun observe(): Flow<List<CatalogStoryProjection>>

    fun observeForStories(storyIds: Set<StoryId>): Flow<List<CatalogStoryProjection>> =
        observe().map { projections -> projections.filter { it.storyId in storyIds } }

    suspend fun find(storyId: StoryId): CatalogStoryProjection? =
        observe().first().firstOrNull { it.storyId == storyId }
}
