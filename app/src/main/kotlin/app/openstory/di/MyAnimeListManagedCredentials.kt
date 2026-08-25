package app.openstory.di

import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialProvider
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest
import java.net.URI

internal class MyAnimeListManagedCredentials(
    clientId: String,
) : ManagedCredentialProvider {
    private val clientId = clientId.trim()

    override suspend fun headers(request: ManagedCredentialRequest): Map<String, String> =
        if (
            request.pluginId.value == PLUGIN_ID &&
            request.hostOrNull() == API_HOST &&
            clientId.matches(SAFE_CLIENT_ID)
        ) {
            mapOf(CLIENT_ID_HEADER to clientId)
        } else {
            emptyMap()
        }

    private fun ManagedCredentialRequest.hostOrNull(): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()

    private companion object {
        const val PLUGIN_ID = "org.openstory.catalog.myanimelist"
        const val API_HOST = "api.myanimelist.net"
        const val CLIENT_ID_HEADER = "X-MAL-CLIENT-ID"
        val SAFE_CLIENT_ID = Regex("[A-Za-z0-9._-]{1,256}")
    }
}
