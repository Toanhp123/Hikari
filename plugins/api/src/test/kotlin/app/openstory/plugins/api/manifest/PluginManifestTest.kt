package app.openstory.plugins.api.manifest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PluginManifestTest {
    @Test
    fun manifestRejectsWildcardNetworkHost() {
        assertFailsWith<IllegalArgumentException> { manifest(networkHosts = setOf("*.example.com")) }
    }

    @Test
    fun manifestUsesSingleJavaScriptEntry() {
        assertFailsWith<IllegalArgumentException> { manifest(entry = "selector.json") }
    }

    @Test
    fun serializedManifestHasNoSelfChecksumOrRuntimeField() {
        val json = Json.encodeToString(PluginManifest.serializer(), manifest())
        assertFalse("packageChecksumSha256" in json)
        assertFalse("\"runtime\"" in json)
    }

    @Test
    fun manifestRetainsDeclaredServices() {
        assertEquals(setOf(PluginService.CATALOG), manifest().provides)
    }

    private fun manifest(
        entry: String = "main.js",
        networkHosts: Set<String> = setOf("api.example.com"),
    ) = PluginManifest(
        id = "org.example.plugin",
        name = "Example plugin",
        version = "1.0.0",
        protocol = PluginProtocolVersion(1),
        entry = entry,
        provides = setOf(PluginService.CATALOG),
        capabilities = PluginCapabilities(NetworkCapability(networkHosts)),
    )
}
