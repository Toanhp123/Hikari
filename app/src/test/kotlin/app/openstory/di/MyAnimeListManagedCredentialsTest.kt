package app.openstory.di

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MyAnimeListManagedCredentialsTest {
    @Test
    fun credentialProviderScopesClientIdToMalApiHost() = runTest {
        val provider = MyAnimeListManagedCredentials(clientId = "marker-secret")

        assertEquals(
            mapOf("X-MAL-CLIENT-ID" to "marker-secret"),
            provider.headers(request("org.openstory.catalog.myanimelist", "https://api.myanimelist.net/v2/manga")),
        )
        assertTrue(provider.headers(request("org.openstory.catalog.myanimelist", "https://cdn.myanimelist.net/a")).isEmpty())
        assertTrue(provider.headers(request("org.example.other", "https://api.myanimelist.net/v2/manga")).isEmpty())
    }

    @Test
    fun invalidOrMissingClientIdIsNeverInjected() = runTest {
        val request = request("org.openstory.catalog.myanimelist", "https://api.myanimelist.net/v2/manga")
        assertTrue(MyAnimeListManagedCredentials(" ").headers(request).isEmpty())
        assertTrue(MyAnimeListManagedCredentials("bad\nvalue").headers(request).isEmpty())
    }

    private fun request(pluginId: String, url: String) =
        ManagedCredentialRequest(PluginId(pluginId), url)
}
