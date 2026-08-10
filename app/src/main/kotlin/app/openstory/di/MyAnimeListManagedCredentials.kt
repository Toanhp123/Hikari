package app.openstory.di

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialProvider

internal class MyAnimeListManagedCredentials(
    clientId: String,
) : ManagedCredentialProvider {
    private val clientId = clientId.trim()

    override suspend fun headers(pluginId: PluginId, host: String): Map<String, String> =
        if (pluginId.value == PLUGIN_ID && host == API_HOST && clientId.matches(SAFE_CLIENT_ID)) {
            mapOf(CLIENT_ID_HEADER to clientId)
        } else {
            emptyMap()
        }

    private companion object {
        const val PLUGIN_ID = "org.openstory.catalog.myanimelist"
        const val API_HOST = "api.myanimelist.net"
        const val CLIENT_ID_HEADER = "X-MAL-CLIENT-ID"
        val SAFE_CLIENT_ID = Regex("[A-Za-z0-9._-]{1,256}")
    }
}
