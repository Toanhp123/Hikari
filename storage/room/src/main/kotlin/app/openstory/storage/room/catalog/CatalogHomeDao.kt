package app.openstory.storage.room.catalog

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CatalogHomeDao {
    @Query("SELECT * FROM catalog_home_snapshots ORDER BY plugin_id")
    fun observeSnapshots(): Flow<List<CatalogHomeSnapshotEntity>>

    @Query("SELECT * FROM catalog_home_sections WHERE plugin_id = :pluginId ORDER BY position")
    suspend fun sections(pluginId: String): List<CatalogHomeSectionEntity>

    @Query("SELECT * FROM catalog_home_sections ORDER BY plugin_id, position")
    fun observeSections(): Flow<List<CatalogHomeSectionEntity>>

    @Query("SELECT * FROM catalog_home_items WHERE plugin_id = :pluginId ORDER BY section_id, position")
    suspend fun items(pluginId: String): List<CatalogHomeItemEntity>

    @Query("SELECT * FROM catalog_home_items ORDER BY plugin_id, section_id, position")
    fun observeItems(): Flow<List<CatalogHomeItemEntity>>

    @Upsert
    suspend fun upsertSnapshot(snapshot: CatalogHomeSnapshotEntity)

    @Upsert
    suspend fun upsertSections(sections: List<CatalogHomeSectionEntity>)

    @Upsert
    suspend fun upsertItems(items: List<CatalogHomeItemEntity>)

    @Query("DELETE FROM catalog_home_sections WHERE plugin_id = :pluginId")
    suspend fun deleteSections(pluginId: String)
}
