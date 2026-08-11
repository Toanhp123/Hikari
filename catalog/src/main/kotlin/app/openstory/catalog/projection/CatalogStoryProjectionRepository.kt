package app.openstory.catalog.projection

import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface CatalogStoryProjectionRepository {
    fun observe(): Flow<List<CatalogStoryProjection>>

    suspend fun find(storyId: StoryId): CatalogStoryProjection? =
        observe().first().firstOrNull { it.storyId == storyId }
}
