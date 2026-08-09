package app.openstory.plugins.runtime.execution

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.NetworkCapability
import app.openstory.plugins.api.manifest.PluginCapabilities
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginProtocolVersion
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PluginOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PluginOperationRunnerTest {
    @Test
    fun operationPathMatchesPublishedWireName() {
        assertEquals("catalog.home", PluginOperation.CATALOG_HOME.wireName)
    }

    private fun manifest() = PluginManifest(
        id = PluginId("org.example.plugin").value,
        name = "Example",
        version = "1.0.0",
        protocol = PluginProtocolVersion(1),
        provides = setOf(PluginService.CATALOG),
        capabilities = PluginCapabilities(NetworkCapability(setOf("api.example.com"))),
    )
}
