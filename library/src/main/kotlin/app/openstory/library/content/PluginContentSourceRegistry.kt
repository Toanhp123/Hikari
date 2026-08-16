package app.openstory.library.content

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginRuntime
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class PluginContentSourceRegistry(
    private val runtime: PluginRuntime,
    private val json: Json,
) : ContentSourceRegistry {
    private val cacheMutex = Mutex()
    private val requestSequence = AtomicLong()
    private val cache = mutableMapOf<SourceIdentity, PluginContentSource>()
    private var latestAppliedRequest = 0L

    override suspend fun enabled(): List<ContentSource> {
        val requestId = requestSequence.incrementAndGet()
        val installed = runtime.enabled(PluginService.CONTENT)
            .sortedBy { plugin -> plugin.pluginId.value }
        return cacheMutex.withLock {
            if (requestId >= latestAppliedRequest) {
                latestAppliedRequest = requestId
                val active = installed.map { plugin -> plugin.identity() }.toSet()
                cache.keys.retainAll(active)
                installed.map(::cachedSource)
            } else {
                installed.map { plugin -> cache[plugin.identity()] ?: newSource(plugin) }
            }
        }
    }

    private fun cachedSource(plugin: InstalledPlugin): PluginContentSource =
        cache.getOrPut(plugin.identity()) { newSource(plugin) }

    private fun newSource(plugin: InstalledPlugin) = PluginContentSource(plugin, runtime, json)
}

private data class SourceIdentity(
    val pluginId: PluginId,
    val version: String,
)

private fun InstalledPlugin.identity() = SourceIdentity(pluginId, version)
