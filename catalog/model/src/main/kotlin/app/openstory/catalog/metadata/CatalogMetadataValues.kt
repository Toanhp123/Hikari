package app.openstory.catalog.metadata

import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.id.PluginId

data class CatalogMetadataKey(
    val pluginId: PluginId,
    val sourceId: String,
) {
    init {
        require(sourceId.isNotBlank())
    }
}

enum class CatalogMetadataLevel {
    Summary,
    Full,
}

data class CatalogMetadataStamp(
    val pluginVersion: String,
    val resolvedAtEpochMillis: Long,
) {
    init {
        require(pluginVersion.isNotBlank())
        require(resolvedAtEpochMillis >= 0)
    }
}

data class CatalogMetadataSnapshot(
    val entry: CatalogEntry,
    val summary: CatalogMetadataStamp,
    val full: CatalogMetadataStamp?,
)
