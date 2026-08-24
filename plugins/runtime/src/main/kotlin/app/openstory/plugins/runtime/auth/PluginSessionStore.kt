package app.openstory.plugins.runtime.auth

import app.openstory.common.id.PluginId

interface PluginSessionStore {
    suspend fun readAll(pluginId: PluginId): List<PluginSessionRecord>
    suspend fun replaceAll(pluginId: PluginId, records: List<PluginSessionRecord>)
    suspend fun clear(pluginId: PluginId)
}
