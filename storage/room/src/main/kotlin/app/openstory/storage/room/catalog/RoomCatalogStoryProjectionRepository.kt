package app.openstory.storage.room.catalog

import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.projection.projectCatalogStory
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class RoomCatalogStoryProjectionRepository internal constructor(
    private val dao: CatalogDao,
) : CatalogStoryProjectionRepository {
    constructor(database: OpenStoryDatabase) : this(database.catalogDao())

    override fun observe(): Flow<List<CatalogStoryProjection>> = project(
        stories = dao.observeStories(),
        entries = dao.observeAllEntries(),
    )

    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<CatalogStoryProjection>> =
        if (storyIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            val ids = storyIds.map(StoryId::value)
            project(
                stories = dao.observeStories(ids),
                entries = dao.observeEntries(ids),
            )
        }

    private fun project(
        stories: Flow<List<StoryEntity>>,
        entries: Flow<List<CatalogEntryEntity>>,
    ): Flow<List<CatalogStoryProjection>> = combine(stories, entries) { storyRows, entryRows ->
        val entriesByStory = entryRows.groupBy(CatalogEntryEntity::storyId)
        storyRows.map { story ->
            projectCatalogStory(
                story = story.toModel(),
                entries = entriesByStory[story.storyId].orEmpty().map(CatalogEntryEntity::toModel),
            )
        }
    }
}
