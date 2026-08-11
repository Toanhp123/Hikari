package app.openstory.library.content

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginRuntime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class PluginContentSourceRegistry(
    private val runtime: PluginRuntime,
    private val json: Json,
) : ContentSourceRegistry {
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<SourceIdentity, PluginContentSource>()

    override suspend fun enabled(): List<ContentSource> = cacheMutex.withLock {
        val installed = runtime.enabled(PluginService.CONTENT)
            .sortedBy { plugin -> plugin.pluginId.value }
        val active = installed.map { plugin -> plugin.identity() }.toSet()
        cache.keys.retainAll(active)
        installed.map { plugin ->
            cache.getOrPut(plugin.identity()) {
                PluginContentSource(plugin, runtime, json)
            }
        }
    }
}

private data class SourceIdentity(
    val pluginId: PluginId,
    val version: String,
)

private fun InstalledPlugin.identity() = SourceIdentity(pluginId, version)
