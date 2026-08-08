package app.openstory.database.repository

import app.openstory.common.AppResult
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.CatalogHomeSnapshot
import app.openstory.model.CatalogSnapshot
import app.openstory.model.CatalogSourceMetadata
import app.openstory.model.PluginId
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    suspend fun ingest(
        snapshot: CatalogSnapshot,
    ): AppResult<Unit>

    suspend fun upsertSourceMetadata(
        pluginId: PluginId,
        pluginVersion: String,
        metadata: CatalogSourceMetadata,
    ): AppResult<CatalogEntryWithStory>

    suspend fun catalogEntry(
        pluginId: PluginId,
        sourceId: String,
    ): AppResult<CatalogEntryWithStory?>

    fun observeCatalogHome(
        pluginId: PluginId,
    ): Flow<CatalogHomeSnapshot?>

    fun observeCatalogHomes():
        Flow<List<CatalogHomeSnapshot>>
}
