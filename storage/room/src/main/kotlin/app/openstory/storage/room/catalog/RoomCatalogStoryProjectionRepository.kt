package app.openstory.storage.room.catalog

import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.projection.toProjection
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomCatalogStoryProjectionRepository internal constructor(
    private val canonical: CanonicalCatalogRepository,
) : CatalogStoryProjectionRepository {
    constructor(database: OpenStoryDatabase) : this(RoomCanonicalCatalogRepository(database))

    override fun observe(): Flow<List<CatalogStoryProjection>> = canonical.observeReadyStories()
        .map { states -> states.map { it.toProjection() } }

    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<CatalogStoryProjection>> =
        if (storyIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            canonical.observeReadyStories(storyIds).map { states ->
                states.map { it.toProjection() }
            }
        }
}
