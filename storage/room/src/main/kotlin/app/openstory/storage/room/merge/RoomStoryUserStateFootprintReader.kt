package app.openstory.storage.room.merge

import app.openstory.catalog.identity.StoryUserStateFootprintReader
import app.openstory.catalog.identity.UserStateFootprint
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase

class RoomStoryUserStateFootprintReader(
    database: OpenStoryDatabase,
) : StoryUserStateFootprintReader {
    private val reader = RoomStoryMergeReaders(database)

    override suspend fun read(storyIds: Set<StoryId>): Map<StoryId, UserStateFootprint> = buildMap {
        storyIds.sortedBy(StoryId::value).forEach { storyId ->
            reader.read(storyId)?.footprint()?.let { footprint -> put(storyId, footprint) }
        }
    }
}
