package app.openstory.plugins.api.manifest

import app.openstory.plugins.api.protocol.PluginOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun manifestDeclaresOperationSupportWithoutBreakingLegacyServiceFallback() {
        val declared = manifest(operations = setOf(PluginOperation.CATALOG_SEARCH))

        assertTrue(declared.supports(PluginOperation.CATALOG_SEARCH))
        assertFalse(declared.supports(PluginOperation.CATALOG_HOME))
        assertTrue(manifest().supports(PluginOperation.CATALOG_HOME))
        assertTrue("\"operations\":[\"catalog.search\"]" in Json.encodeToString(declared))
    }

    @Test
    fun manifestRejectsOperationsOutsideDeclaredServices() {
        assertFailsWith<IllegalArgumentException> {
            manifest(operations = setOf(PluginOperation.CONTENT_CHAPTER))
        }
    }

    @Test
    fun manifestRetainsDeclaredServices() {
        assertEquals(setOf(PluginService.CATALOG), manifest().provides)
    }

    private fun manifest(
        entry: String = "main.js",
        networkHosts: Set<String> = setOf("api.example.com"),
        operations: Set<PluginOperation>? = null,
    ) = PluginManifest(
        id = "org.example.plugin",
        name = "Example plugin",
        version = "1.0.0",
        protocol = PluginProtocolVersion(1),
        entry = entry,
        provides = setOf(PluginService.CATALOG),
        operations = operations,
        capabilities = PluginCapabilities(NetworkCapability(networkHosts)),
    )
}
