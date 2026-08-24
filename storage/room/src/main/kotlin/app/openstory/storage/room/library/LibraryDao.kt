package app.openstory.storage.room.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

internal data class ChapterSyncCandidateRow(
    @androidx.room.ColumnInfo(name = "story_id") val storyId: String,
    @androidx.room.ColumnInfo(name = "last_successful_sync_at_epoch_millis")
    val lastSuccessfulSyncAtEpochMillis: Long?,
)

@Dao
internal interface LibraryDao {
    @Query(
        "SELECT library.story_id AS story_id, " +
            "MIN(sync.updated_at_epoch_millis) AS last_successful_sync_at_epoch_millis " +
            "FROM library_entries AS library " +
            "LEFT JOIN chapter_sync_states AS sync ON sync.story_id = library.story_id " +
            "GROUP BY library.story_id " +
            "ORDER BY last_successful_sync_at_epoch_millis IS NOT NULL, " +
            "last_successful_sync_at_epoch_millis, library.story_id",
    )
    suspend fun chapterSyncCandidates(): List<ChapterSyncCandidateRow>

    @Query("SELECT * FROM library_entries ORDER BY story_id")
    fun observe(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library_entries WHERE story_id = :storyId")
    suspend fun find(storyId: String): LibraryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: LibraryEntity): Long

    @Query("DELETE FROM library_entries WHERE story_id = :storyId")
    suspend fun delete(storyId: String): Int

    @Query(
        "UPDATE library_entries SET status = :status, " +
            "updated_at_epoch_millis = :updatedAtEpochMillis WHERE story_id = :storyId",
    )
    suspend fun updateStatus(
        storyId: String,
        status: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("SELECT * FROM content_mappings ORDER BY story_id, plugin_id")
    fun observeMappings(): Flow<List<ContentMappingEntity>>

    @Query("SELECT * FROM content_mappings WHERE story_id IN (:storyIds) ORDER BY story_id, plugin_id")
    fun observeMappings(storyIds: Collection<String>): Flow<List<ContentMappingEntity>>

    @Query("SELECT * FROM content_mappings WHERE story_id = :storyId ORDER BY plugin_id")
    fun observeMappings(storyId: String): Flow<List<ContentMappingEntity>>

    @Query("SELECT * FROM content_mappings WHERE story_id = :storyId AND plugin_id = :pluginId")
    suspend fun findMapping(storyId: String, pluginId: String): ContentMappingEntity?

    @Query("SELECT * FROM content_mappings WHERE story_id = :storyId ORDER BY plugin_id")
    suspend fun mappingsForStory(storyId: String): List<ContentMappingEntity>

    @Query(
        "SELECT * FROM content_mapping_rejections WHERE story_id = :storyId " +
            "ORDER BY plugin_id, source_story_id, policy_version",
    )
    suspend fun rejectionsForStory(storyId: String): List<ContentMappingRejectionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMapping(entity: ContentMappingEntity): Long

    @Update
    suspend fun updateMapping(entity: ContentMappingEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRejection(entity: ContentMappingRejectionEntity)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM content_mapping_rejections " +
            "WHERE story_id = :storyId AND plugin_id = :pluginId " +
            "AND source_story_id = :sourceStoryId AND policy_version = :policyVersion)",
    )
    suspend fun isRejected(
        storyId: String,
        pluginId: String,
        sourceStoryId: String,
        policyVersion: Int,
    ): Boolean
    @Upsert
    suspend fun upsertLibrary(entity: LibraryEntity)

    @Query("DELETE FROM content_mappings WHERE story_id IN (:storyIds)")
    suspend fun deleteMappingsForStories(storyIds: Collection<String>): Int

    @Upsert
    suspend fun upsertMappings(entities: List<ContentMappingEntity>)

    @Query("DELETE FROM content_mapping_rejections WHERE story_id IN (:storyIds)")
    suspend fun deleteRejectionsForStories(storyIds: Collection<String>): Int

    @Upsert
    suspend fun upsertRejections(entities: List<ContentMappingRejectionEntity>)

}
