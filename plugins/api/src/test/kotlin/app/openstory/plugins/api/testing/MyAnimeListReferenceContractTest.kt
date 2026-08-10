package app.openstory.plugins.api.testing

import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginService
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MyAnimeListReferenceContractTest {
    @Test
    fun vnextManifestContainsOnlyProtocolRuntimeContract() {
        val raw = checkNotNull(javaClass.getResource("/reference-plugins/myanimelist/manifest.json")).readText()
        val manifest = Json.decodeFromString<PluginManifest>(raw)

        assertEquals("org.openstory.catalog.myanimelist", manifest.id)
        assertEquals("2.0.0", manifest.version)
        assertEquals(1, manifest.protocol.major)
        assertEquals(setOf(PluginService.CATALOG), manifest.provides)
        assertEquals("main.js", manifest.entry)
        assertFalse("packageChecksumSha256" in raw)
        assertFalse("\"runtime\"" in raw)
    }
}
