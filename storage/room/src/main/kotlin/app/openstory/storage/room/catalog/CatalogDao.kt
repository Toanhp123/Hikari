package app.openstory.storage.room.catalog

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query("SELECT * FROM catalog_entries WHERE story_id = :storyId ORDER BY plugin_id, source_id")
    suspend fun entriesForStory(storyId: String): List<CatalogEntryEntity>

    @Query("SELECT * FROM catalog_entries ORDER BY story_id, plugin_id, source_id")
    suspend fun allEntries(): List<CatalogEntryEntity>

    @Query("SELECT * FROM catalog_entries ORDER BY plugin_id, source_id")
    fun observeAllEntries(): Flow<List<CatalogEntryEntity>>

    @Query(
        "SELECT DISTINCT entry.* FROM catalog_entries AS entry " +
            "INNER JOIN catalog_home_items AS item " +
            "ON item.plugin_id = entry.plugin_id AND item.source_id = entry.source_id " +
            "ORDER BY entry.plugin_id, entry.source_id",
    )
    suspend fun homeEntries(): List<CatalogEntryEntity>

    @Query(
        "SELECT * FROM catalog_entries WHERE plugin_id = :pluginId AND source_id IN (:sourceIds) " +
            "ORDER BY source_id",
    )
    suspend fun entries(pluginId: String, sourceIds: Collection<String>): List<CatalogEntryEntity>

    @Query(
        "SELECT * FROM catalog_entries WHERE story_id IN (:storyIds) " +
            "ORDER BY plugin_id, source_id",
    )
    fun observeEntries(storyIds: Collection<String>): Flow<List<CatalogEntryEntity>>

    @Query("SELECT * FROM catalog_entries WHERE story_id = :storyId ORDER BY plugin_id, source_id")
    fun observeEntries(storyId: String): Flow<List<CatalogEntryEntity>>

    @Query("SELECT * FROM catalog_entries WHERE plugin_id = :pluginId AND source_id = :sourceId")
    suspend fun findEntry(pluginId: String, sourceId: String): CatalogEntryEntity?

    @Query(
        "SELECT * FROM catalog_entry_identifiers " +
            "WHERE plugin_id = :pluginId AND source_id = :sourceId " +
            "ORDER BY namespace, scope, value",
    )
    suspend fun identifiers(pluginId: String, sourceId: String): List<CatalogEntryIdentifierEntity>

    @Query(
        "SELECT * FROM catalog_entry_identifiers " +
            "ORDER BY plugin_id, source_id, namespace, scope, value",
    )
    suspend fun allIdentifiers(): List<CatalogEntryIdentifierEntity>

    @Query(
        "SELECT identifier.* FROM catalog_entry_identifiers AS identifier " +
            "INNER JOIN catalog_entries AS entry ON entry.plugin_id = identifier.plugin_id " +
            "AND entry.source_id = identifier.source_id WHERE entry.story_id IN (:storyIds) " +
            "ORDER BY identifier.plugin_id, identifier.source_id, identifier.namespace, " +
            "identifier.scope, identifier.value",
    )
    suspend fun identifiersForStories(storyIds: Collection<String>): List<CatalogEntryIdentifierEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentifiers(identifiers: List<CatalogEntryIdentifierEntity>)

    @Query("DELETE FROM catalog_entry_identifiers WHERE plugin_id = :pluginId AND source_id = :sourceId")
    suspend fun deleteIdentifiers(pluginId: String, sourceId: String)

    @Query("DELETE FROM catalog_entry_identifiers WHERE plugin_id = :pluginId AND source_id IN (:sourceIds)")
    suspend fun deleteIdentifiers(pluginId: String, sourceIds: Collection<String>)

    @Query("SELECT * FROM stories WHERE story_id = :storyId")
    fun observeStory(storyId: String): Flow<StoryEntity?>

    @Query("SELECT * FROM stories WHERE story_id = :storyId")
    suspend fun findStory(storyId: String): StoryEntity?

    @Query("SELECT story_id FROM stories WHERE story_id IN (:storyIds)")
    suspend fun existingStoryIds(storyIds: Collection<String>): List<String>

    @Upsert suspend fun upsertStories(stories: List<StoryEntity>)
    @Upsert suspend fun upsertEntries(entries: List<CatalogEntryEntity>)

    @Query("UPDATE catalog_entries SET story_id = :survivorStoryId WHERE story_id = :retiredStoryId")
    suspend fun moveEntries(retiredStoryId: String, survivorStoryId: String): Int

    @Query(
        "UPDATE catalog_entries SET story_id = :newStoryId WHERE plugin_id = :pluginId AND source_id = :sourceId " +
            "AND story_id = :expectedStoryId",
    )
    suspend fun moveEntry(
        pluginId: String,
        sourceId: String,
        expectedStoryId: String,
        newStoryId: String,
    ): Int

    @Query("DELETE FROM stories WHERE story_id = :storyId")
    suspend fun deleteStory(storyId: String): Int
}
