package app.openstory.storage.room.catalog

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CatalogDao {
    @Query("SELECT * FROM stories ORDER BY story_id")
    suspend fun stories(): List<StoryEntity>

    @Query("SELECT * FROM stories ORDER BY story_id")
    fun observeStories(): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE story_id IN (:storyIds) ORDER BY story_id")
    fun observeStories(storyIds: Collection<String>): Flow<List<StoryEntity>>

    @Query("SELECT * FROM catalog_entries ORDER BY plugin_id, source_id")
    suspend fun entries(): List<CatalogEntryEntity>

    @Query("SELECT * FROM catalog_entries ORDER BY plugin_id, source_id")
    fun observeAllEntries(): Flow<List<CatalogEntryEntity>>

    @Query(
        "SELECT * FROM catalog_entries WHERE story_id IN (:storyIds) " +
            "ORDER BY plugin_id, source_id",
    )
    fun observeEntries(storyIds: Collection<String>): Flow<List<CatalogEntryEntity>>

    @Query("SELECT * FROM catalog_entries WHERE story_id = :storyId ORDER BY plugin_id, source_id")
    fun observeEntries(storyId: String): Flow<List<CatalogEntryEntity>>

    @Query("SELECT * FROM catalog_entries WHERE plugin_id = :pluginId AND source_id = :sourceId")
    suspend fun findEntry(pluginId: String, sourceId: String): CatalogEntryEntity?

    @Query("SELECT * FROM stories WHERE story_id = :storyId")
    fun observeStory(storyId: String): Flow<StoryEntity?>

    @Query("SELECT * FROM stories WHERE story_id = :storyId")
    suspend fun findStory(storyId: String): StoryEntity?

    @Upsert suspend fun upsertStories(stories: List<StoryEntity>)
    @Upsert suspend fun upsertEntries(entries: List<CatalogEntryEntity>)
}
