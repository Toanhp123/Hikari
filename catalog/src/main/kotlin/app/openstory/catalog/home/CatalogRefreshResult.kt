package app.openstory.catalog.home

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.source.CatalogSourceFailure
import app.openstory.common.id.PluginId

sealed interface CatalogRefreshResult {
    val pluginId: PluginId

    data class Success(
        override val pluginId: PluginId,
        val refreshedAtEpochMillis: Long,
    ) : CatalogRefreshResult
    data class SourceFailure(override val pluginId: PluginId, val failure: CatalogSourceFailure) : CatalogRefreshResult
    data class StoreFailure(override val pluginId: PluginId, val failure: CatalogStoreFailure) : CatalogRefreshResult
}
