package app.openstory.library.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface LibraryDao {
    @Query("SELECT * FROM library_entry WHERE story_id = :storyId")
    fun observeMembership(storyId: String): Flow<LibraryEntryEntity?>

    @Query("SELECT * FROM library_entry WHERE story_id = :storyId")
    suspend fun entry(storyId: String): LibraryEntryEntity?

    @Query(
        """
        SELECT * FROM library_entry
        ORDER BY saved_at_epoch_ms DESC, story_id ASC
        LIMIT :limit
        """,
    )
    fun observeUnfilteredFirst(limit: Int): Flow<List<LibraryEntryEntity>>

    @Query(
        """
        SELECT * FROM library_entry
        WHERE saved_at_epoch_ms < :afterSavedAt
           OR (saved_at_epoch_ms = :afterSavedAt AND story_id > :afterStoryId)
        ORDER BY saved_at_epoch_ms DESC, story_id ASC
        LIMIT :limit
        """,
    )
    fun observeUnfilteredAfter(
        afterSavedAt: Long,
        afterStoryId: String,
        limit: Int,
    ): Flow<List<LibraryEntryEntity>>

    @Query(
        """
        SELECT * FROM library_entry
        WHERE origin_media_context = :mediaType
        ORDER BY saved_at_epoch_ms DESC, story_id ASC
        LIMIT :limit
        """,
    )
    fun observeFilteredFirst(mediaType: String, limit: Int): Flow<List<LibraryEntryEntity>>

    @Query(
        """
        SELECT * FROM library_entry
        WHERE origin_media_context = :mediaType
          AND (
              saved_at_epoch_ms < :afterSavedAt
              OR (saved_at_epoch_ms = :afterSavedAt AND story_id > :afterStoryId)
          )
        ORDER BY saved_at_epoch_ms DESC, story_id ASC
        LIMIT :limit
        """,
    )
    fun observeFilteredAfter(
        mediaType: String,
        afterSavedAt: Long,
        afterStoryId: String,
        limit: Int,
    ): Flow<List<LibraryEntryEntity>>

    @Query(
        """
        SELECT entry.* FROM library_entry AS entry
        INNER JOIN library_search_fts ON library_search_fts.story_id = entry.story_id
        WHERE library_search_fts MATCH :matchQuery
        ORDER BY entry.saved_at_epoch_ms DESC, entry.story_id ASC
        LIMIT :limit
        """,
    )
    fun observeSearchFirst(matchQuery: String, limit: Int): Flow<List<LibraryEntryEntity>>

    @Query(
        """
        SELECT entry.* FROM library_entry AS entry
        INNER JOIN library_search_fts ON library_search_fts.story_id = entry.story_id
        WHERE library_search_fts MATCH :matchQuery
          AND (
              entry.saved_at_epoch_ms < :afterSavedAt
              OR (entry.saved_at_epoch_ms = :afterSavedAt AND entry.story_id > :afterStoryId)
          )
        ORDER BY entry.saved_at_epoch_ms DESC, entry.story_id ASC
        LIMIT :limit
        """,
    )
    fun observeSearchAfter(
        matchQuery: String,
        afterSavedAt: Long,
        afterStoryId: String,
        limit: Int,
    ): Flow<List<LibraryEntryEntity>>

    @Query(
        """
        SELECT entry.* FROM library_entry AS entry
        INNER JOIN library_search_fts ON library_search_fts.story_id = entry.story_id
        WHERE library_search_fts MATCH :matchQuery
          AND entry.origin_media_context = :mediaType
        ORDER BY entry.saved_at_epoch_ms DESC, entry.story_id ASC
        LIMIT :limit
        """,
    )
    fun observeFilteredSearchFirst(
        matchQuery: String,
        mediaType: String,
        limit: Int,
    ): Flow<List<LibraryEntryEntity>>

    @Query(
        """
        SELECT entry.* FROM library_entry AS entry
        INNER JOIN library_search_fts ON library_search_fts.story_id = entry.story_id
        WHERE library_search_fts MATCH :matchQuery
          AND entry.origin_media_context = :mediaType
          AND (
              entry.saved_at_epoch_ms < :afterSavedAt
              OR (entry.saved_at_epoch_ms = :afterSavedAt AND entry.story_id > :afterStoryId)
          )
        ORDER BY entry.saved_at_epoch_ms DESC, entry.story_id ASC
        LIMIT :limit
        """,
    )
    fun observeFilteredSearchAfter(
        matchQuery: String,
        mediaType: String,
        afterSavedAt: Long,
        afterStoryId: String,
        limit: Int,
    ): Flow<List<LibraryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entity: LibraryEntryEntity): Long

    @Update
    suspend fun updateEntry(entity: LibraryEntryEntity): Int

    @Query("DELETE FROM library_entry WHERE story_id = :storyId")
    suspend fun deleteEntry(storyId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSearch(entity: LibrarySearchFts)

    @Query("DELETE FROM library_search_fts WHERE story_id = :storyId")
    suspend fun deleteSearch(storyId: String): Int
}
