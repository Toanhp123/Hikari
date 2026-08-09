package app.openstory.di

import app.openstory.common.id.PluginId
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
            provider.headers(PluginId("org.openstory.catalog.myanimelist"), "api.myanimelist.net"),
        )
        assertTrue(provider.headers(PluginId("org.openstory.catalog.myanimelist"), "cdn.myanimelist.net").isEmpty())
        assertTrue(provider.headers(PluginId("org.example.other"), "api.myanimelist.net").isEmpty())
    }

    @Test
    fun invalidOrMissingClientIdIsNeverInjected() = runTest {
        val pluginId = PluginId("org.openstory.catalog.myanimelist")
        assertTrue(MyAnimeListManagedCredentials(" ").headers(pluginId, "api.myanimelist.net").isEmpty())
        assertTrue(MyAnimeListManagedCredentials("bad\nvalue").headers(pluginId, "api.myanimelist.net").isEmpty())
    }
}
