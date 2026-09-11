package app.openstory.catalog.storage.retention

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal interface StoryRetentionDao {
    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM discover_card AS card
            INNER JOIN catalog_source_state AS state
                ON state.source_key = card.source_key
                AND state.media_type = card.media_type
                AND state.published_generation = card.generation
            WHERE card.story_id = :storyId
        )
        """,
    )
    suspend fun isReachableFromAnyCurrentDiscover(storyId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM story_detail WHERE story_id = :storyId)")
    suspend fun hasDetail(storyId: String): Boolean

    @Transaction
    suspend fun touchOrphan(storyId: String, lastAccessedEpochMs: Long) {
        val updated = touchExistingOrphan(storyId, lastAccessedEpochMs)
        if (updated == 0) {
            insertOrphan(StoryRetentionEntity(storyId, lastAccessedEpochMs))
        }
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrphan(entity: StoryRetentionEntity)

    @Query(
        """
        UPDATE story_orphan_retention
        SET last_accessed_epoch_ms = MAX(last_accessed_epoch_ms, :lastAccessedEpochMs)
        WHERE story_id = :storyId
        """,
    )
    suspend fun touchExistingOrphan(storyId: String, lastAccessedEpochMs: Long): Int

    @Query("DELETE FROM story_orphan_retention WHERE story_id = :storyId")
    suspend fun removeOrphan(storyId: String): Int

    @Query(
        """
        SELECT * FROM story_orphan_retention
        ORDER BY last_accessed_epoch_ms ASC, story_id ASC
        LIMIT 65
        """,
    )
    suspend fun oldestOrphans(): List<StoryRetentionEntity>

    @Query(
        """
        SELECT * FROM story_orphan_retention
        WHERE story_id NOT IN (:excludedStoryIds)
        ORDER BY last_accessed_epoch_ms ASC, story_id ASC
        LIMIT 1
        """,
    )
    suspend fun oldestOrphanExcluding(excludedStoryIds: Set<String>): StoryRetentionEntity?

    @Query("DELETE FROM story_orphan_retention WHERE story_id IN (:storyIds)")
    suspend fun removeOrphans(storyIds: Set<String>): Int

    @Query(
        """
        DELETE FROM story_source_identity
        WHERE story_id IN (:storyIds)
            AND NOT EXISTS(
                SELECT 1
                FROM discover_card AS card
                INNER JOIN catalog_source_state AS state
                    ON state.source_key = card.source_key
                    AND state.media_type = card.media_type
                    AND state.published_generation = card.generation
                WHERE card.story_id = story_source_identity.story_id
            )
        """,
    )
    suspend fun deleteUnreachableStories(storyIds: Set<String>): Int
}
