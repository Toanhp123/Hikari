package app.openstory.storage.room.catalog

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface CatalogHomeDao {
    @Query("SELECT * FROM catalog_home_snapshots ORDER BY plugin_id")
    suspend fun snapshots(): List<CatalogHomeSnapshotEntity>

    @Query("SELECT * FROM catalog_home_sections ORDER BY plugin_id, position")
    suspend fun sections(): List<CatalogHomeSectionEntity>

    @Query("SELECT * FROM catalog_home_items ORDER BY plugin_id, section_id, position")
    suspend fun items(): List<CatalogHomeItemEntity>

    @Upsert
    suspend fun upsertSnapshot(snapshot: CatalogHomeSnapshotEntity)

    @Upsert
    suspend fun upsertSections(sections: List<CatalogHomeSectionEntity>)

    @Upsert
    suspend fun upsertItems(items: List<CatalogHomeItemEntity>)

    @Query("DELETE FROM catalog_home_sections WHERE plugin_id = :pluginId")
    suspend fun deleteSections(pluginId: String)
}
