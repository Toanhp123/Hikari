package app.openstory.plugins.runtime.capabilities.http

import app.openstory.common.id.PluginId

fun interface ManagedCredentialProvider {
    suspend fun headers(pluginId: PluginId, host: String): Map<String, String>

    companion object {
        val NONE = ManagedCredentialProvider { _, _ -> emptyMap() }
    }
}
