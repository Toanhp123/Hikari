package app.openstory.catalog.source

import app.openstory.common.id.PluginId

class ExclusiveCatalogSourceRegistry(
    private val fallback: CatalogSourceRegistry,
    exclusive: Set<CatalogSource>,
) : CatalogSourceRegistry {
    private val exclusiveByPluginId = exclusive.associateBy(CatalogSource::pluginId)

    override suspend fun enabled(): List<CatalogSource> = if (exclusiveByPluginId.isEmpty()) {
        fallback.enabled()
    } else {
        exclusiveByPluginId.values.sortedBy { it.pluginId.value }
    }

    override suspend fun source(pluginId: PluginId): CatalogSource? = if (exclusiveByPluginId.isEmpty()) {
        fallback.source(pluginId)
    } else {
        exclusiveByPluginId[pluginId]
    }
}
