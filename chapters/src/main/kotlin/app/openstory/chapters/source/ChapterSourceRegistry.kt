package app.openstory.chapters.source

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginRuntime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

interface ChapterSourceRegistry {
    suspend fun enabled(): List<ChapterSource>
}

class PluginChapterSourceRegistry(
    private val runtime: PluginRuntime,
    private val json: Json,
) : ChapterSourceRegistry {
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<SourceIdentity, PluginChapterSource>()

    override suspend fun enabled(): List<ChapterSource> = cacheMutex.withLock {
        val installed = runtime.enabled(PluginOperation.CONTENT_CHAPTERS).sortedBy { plugin -> plugin.pluginId.value }
        val active = installed.map(InstalledPlugin::identity).toSet()
        cache.keys.retainAll(active)
        installed.map { plugin ->
            cache.getOrPut(plugin.identity()) { PluginChapterSource(plugin, runtime, json) }
        }
    }
}

private data class SourceIdentity(
    val pluginId: PluginId,
    val version: String,
)

private fun InstalledPlugin.identity() = SourceIdentity(pluginId, version)
