package app.openstory.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.openstory.database.entity.ReadingProgressEntity
import app.openstory.model.ReaderPosition

@Dao
internal abstract class ProgressDao {

    @Insert(
        onConflict = OnConflictStrategy.IGNORE,
    )
    protected abstract suspend fun insertIfMissing(
        progress: ReadingProgressEntity,
    ): Long

    @Query(
        """
        UPDATE reading_progress
        SET
            release_id = :releaseId,
            position = :position,
            completed = :completed,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE story_id = :storyId
            AND chapter_id = :chapterId
            AND updated_at_epoch_millis < :updatedAtEpochMillis
        """,
    )
    protected abstract suspend fun updateIfNotStale(
        storyId: String,
        chapterId: String,
        releaseId: String?,
        position: ReaderPosition,
        completed: Boolean,
        updatedAtEpochMillis: Long,
    ): Int

    @Transaction
    open suspend fun upsertProgress(
        progress: ReadingProgressEntity,
    ) {
        val insertedRowId =
            insertIfMissing(progress)

        if (insertedRowId == INSERT_CONFLICT) {
            updateIfNotStale(
                storyId = progress.storyId,
                chapterId = progress.chapterId,
                releaseId = progress.releaseId,
                position = progress.position,
                completed = progress.completed,
                updatedAtEpochMillis =
                    progress.updatedAtEpochMillis,
            )
        }
    }

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}
