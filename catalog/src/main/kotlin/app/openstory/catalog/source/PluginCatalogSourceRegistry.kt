package app.openstory.catalog.source

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.PluginRuntime
import kotlinx.serialization.json.Json

class PluginCatalogSourceRegistry(
    private val runtime: PluginRuntime,
    private val json: Json,
) : CatalogSourceRegistry {
    override suspend fun enabled(): List<CatalogSource> = runtime.enabled(PluginService.CATALOG)
        .map { installed -> PluginCatalogSource(installed, runtime, json) }

    override suspend fun source(pluginId: PluginId): CatalogSource? = enabled()
        .firstOrNull { source -> source.pluginId == pluginId }
}
