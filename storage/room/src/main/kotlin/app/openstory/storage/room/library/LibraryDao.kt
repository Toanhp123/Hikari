package app.openstory.storage.room.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface LibraryDao {
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
}
