package app.openstory.storage.room.downloads

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DownloadDao {
    @Query(
        "SELECT * FROM chapter_storage_entries " +
            "WHERE namespace = 'EXPLICIT_DOWNLOAD' AND download_state IS NOT NULL " +
            "ORDER BY updated_at_epoch_millis DESC, chapter_release_id ASC",
    )
    fun observeAllDownloads(): Flow<List<ChapterStorageEntryEntity>>

    @Query(
        "SELECT COUNT(*) FROM chapter_storage_entries " +
            "WHERE namespace = 'EXPLICIT_DOWNLOAD' AND download_state = 'COMPLETED'",
    )
    fun observeCompletedDownloadCount(): Flow<Int>

    @Query("SELECT * FROM chapter_storage_entries")
    suspend fun allEntries(): List<ChapterStorageEntryEntity>

    @Query("SELECT * FROM chapter_storage_entries WHERE checksum IS NOT NULL")
    suspend fun storedEntries(): List<ChapterStorageEntryEntity>

    @Query(
        "SELECT COALESCE(SUM(size_bytes), 0) FROM chapter_storage_entries " +
            "WHERE namespace = 'AUTOMATIC_CACHE' AND checksum IS NOT NULL",
    )
    suspend fun automaticCacheUsageBytes(): Long

    @Query(
        "SELECT * FROM chapter_storage_entries " +
            "WHERE namespace = 'AUTOMATIC_CACHE' AND checksum IS NOT NULL " +
            "ORDER BY last_accessed_at_epoch_millis ASC, chapter_release_id ASC",
    )
    suspend fun automaticCacheEntriesByLru(): List<ChapterStorageEntryEntity>

    @Query(
        "SELECT * FROM chapter_storage_entries WHERE namespace = :namespace " +
            "AND chapter_release_id = :releaseId AND content_fingerprint = :fingerprint",
    )
    suspend fun find(namespace: String, releaseId: String, fingerprint: String): ChapterStorageEntryEntity?

    @Query(
        "SELECT * FROM chapter_storage_entries " +
            "WHERE chapter_release_id IN (:releaseIds) " +
            "AND namespace IN ('EXPLICIT_DOWNLOAD', 'AUTOMATIC_CACHE')",
    )
    suspend fun readerEntries(releaseIds: List<String>): List<ChapterStorageEntryEntity>

    @Query(
        "SELECT * FROM chapter_storage_entries WHERE namespace = 'EXPLICIT_DOWNLOAD' " +
            "AND chapter_release_id = :releaseId ORDER BY updated_at_epoch_millis DESC LIMIT 1",
    )
    suspend fun findDownload(releaseId: String): ChapterStorageEntryEntity?

    @Query(
        "SELECT * FROM chapter_storage_entries WHERE namespace = 'EXPLICIT_DOWNLOAD' " +
            "AND chapter_release_id = :releaseId ORDER BY updated_at_epoch_millis DESC LIMIT 1",
    )
    fun observeDownload(releaseId: String): Flow<ChapterStorageEntryEntity?>

    @Query(
        "DELETE FROM chapter_storage_entries WHERE namespace = 'EXPLICIT_DOWNLOAD' " +
            "AND chapter_release_id = :releaseId",
    )
    suspend fun deleteDownload(releaseId: String)

    @Query(
        "DELETE FROM chapter_storage_entries WHERE namespace = 'AUTOMATIC_CACHE' " +
            "AND chapter_release_id = :releaseId AND content_fingerprint = :fingerprint",
    )
    suspend fun deleteAutomaticCache(releaseId: String, fingerprint: String): Int

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
