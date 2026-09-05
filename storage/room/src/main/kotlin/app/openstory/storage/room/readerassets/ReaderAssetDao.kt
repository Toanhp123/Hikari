package app.openstory.storage.room.readerassets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface ReaderAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReaderAssetEntryEntity)

    @Query("SELECT * FROM reader_asset_entries WHERE logical_asset_key_hash IN (:hashes)")
    suspend fun find(hashes: List<String>): List<ReaderAssetEntryEntity>

    @Query("SELECT * FROM reader_asset_entries ORDER BY logical_asset_key_hash")
    suspend fun all(): List<ReaderAssetEntryEntity>

    @Query("SELECT COALESCE(SUM(byte_size), 0) FROM reader_asset_entries")
    suspend fun usageBytes(): Long

    @Query("SELECT * FROM reader_asset_entries WHERE logical_asset_key_hash = :hash")
    suspend fun findOne(hash: String): ReaderAssetEntryEntity?

    @Query("DELETE FROM reader_asset_entries WHERE logical_asset_key_hash = :hash")
    suspend fun deleteOne(hash: String)

    @Query("DELETE FROM reader_asset_entries")
    suspend fun deleteAll()

    @Query(
        "SELECT * FROM reader_asset_entries WHERE source_namespace = :sourceNamespace " +
            "ORDER BY logical_asset_key_hash",
    )
    suspend fun findBySource(sourceNamespace: String): List<ReaderAssetEntryEntity>

    @Query("DELETE FROM reader_asset_entries WHERE source_namespace = :sourceNamespace")
    suspend fun deleteBySource(sourceNamespace: String)

    @Query(
        "SELECT * FROM reader_asset_entries WHERE source_namespace = :sourceNamespace " +
            "AND security_scope_hash = :securityScopeHash ORDER BY logical_asset_key_hash",
    )
    suspend fun findByAccount(
        sourceNamespace: String,
        securityScopeHash: String,
    ): List<ReaderAssetEntryEntity>

    @Query(
        "DELETE FROM reader_asset_entries WHERE source_namespace = :sourceNamespace " +
            "AND security_scope_hash = :securityScopeHash",
    )
    suspend fun deleteByAccount(sourceNamespace: String, securityScopeHash: String)

    @Query(
        "SELECT * FROM reader_asset_entries WHERE source_namespace = :sourceNamespace " +
            "AND security_scope_hash IS NOT NULL ORDER BY logical_asset_key_hash",
    )
    suspend fun findAllAccountsForSource(sourceNamespace: String): List<ReaderAssetEntryEntity>

    @Query(
        "DELETE FROM reader_asset_entries WHERE source_namespace = :sourceNamespace " +
            "AND security_scope_hash IS NOT NULL",
    )
    suspend fun deleteAllAccountsForSource(sourceNamespace: String)

    @Query(
        "UPDATE reader_asset_entries SET last_accessed_at_epoch_millis = :epochMillis " +
            "WHERE logical_asset_key_hash = :hash",
    )
    suspend fun updateLastAccessed(hash: String, epochMillis: Long)

    @Query(
        "UPDATE reader_asset_entries SET last_consumed_at_epoch_millis = :epochMillis " +
            "WHERE logical_asset_key_hash = :hash",
    )
    suspend fun updateLastConsumed(hash: String, epochMillis: Long)
}
