package app.openstory.storage.room.catalog

import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.projection.projectCatalogStory
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomCatalogStoryProjectionRepository internal constructor(
    private val dao: CatalogDao,
) : CatalogStoryProjectionRepository {
    constructor(database: OpenStoryDatabase) : this(database.catalogDao())

    override fun observe(): Flow<List<CatalogStoryProjection>> = combine(
        dao.observeStories(),
        dao.observeAllEntries(),
    ) { stories, entries ->
        val entriesByStory = entries.groupBy(CatalogEntryEntity::storyId)
        stories.map { story ->
            projectCatalogStory(
                story = story.toModel(),
                entries = entriesByStory[story.storyId].orEmpty().map(CatalogEntryEntity::toModel),
            )
        }
    }
}
