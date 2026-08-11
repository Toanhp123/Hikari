package app.openstory.storage.room.downloads

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface DownloadDao {
    @Query("SELECT * FROM chapter_storage_entries WHERE checksum IS NOT NULL")
    suspend fun storedEntries(): List<ChapterStorageEntryEntity>

    @Query(
        "SELECT * FROM chapter_storage_entries WHERE namespace = :namespace " +
            "AND chapter_release_id = :releaseId AND content_fingerprint = :fingerprint",
    )
    suspend fun find(namespace: String, releaseId: String, fingerprint: String): ChapterStorageEntryEntity?

    @Upsert
    suspend fun upsert(entry: ChapterStorageEntryEntity)

    @Query(
        "UPDATE chapter_storage_entries SET last_accessed_at_epoch_millis = :accessedAt, " +
            "updated_at_epoch_millis = :accessedAt WHERE namespace = :namespace " +
            "AND chapter_release_id = :releaseId AND content_fingerprint = :fingerprint",
    )
    suspend fun touch(namespace: String, releaseId: String, fingerprint: String, accessedAt: Long)

    @Delete
    suspend fun delete(entry: ChapterStorageEntryEntity)
}
