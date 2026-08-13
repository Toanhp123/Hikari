package app.openstory.storage.room.reader

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ReadingProgressDao {
    @Query(
        "SELECT * FROM reading_progress " +
            "ORDER BY updated_at_epoch_millis DESC, story_id ASC, canonical_chapter_id ASC",
    )
    fun observeAll(): Flow<List<ReadingProgressEntity>>

    @Query(
        "SELECT * FROM reading_progress WHERE story_id = :storyId " +
            "AND canonical_chapter_id = :chapterId",
    )
    fun observe(storyId: String, chapterId: String): Flow<ReadingProgressEntity?>

    @Query(
        "SELECT * FROM reading_progress WHERE story_id = :storyId " +
            "AND canonical_chapter_id = :chapterId",
    )
    suspend fun find(storyId: String, chapterId: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)
}
