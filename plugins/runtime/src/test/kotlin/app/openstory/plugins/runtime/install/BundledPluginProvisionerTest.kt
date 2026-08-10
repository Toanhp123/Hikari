package app.openstory.plugins.runtime.install

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import app.openstory.plugins.runtime.update.PluginUpdateService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class BundledPluginProvisionerTest {
    @Test
    fun bundledProvisionerNeverDowngradesNewerInstalledVersion() = runTest {
        val pluginId = PluginId("org.example.plugin")
        val state = MemoryStateStore(
            listOf(
                StoredPluginState(
                    pluginId,
                    setOf(PluginService.CATALOG),
                    true,
                    StoredPluginVersion("3.0.0", "/3", "a".repeat(64), null),
                    null,
                    setOf("api.example.com"),
                ),
            ),
        )
        val installer = PluginInstaller(PackageVerifier(), MemoryPackageStorage(), state)
        val source = BundledPluginSource {
            listOf(
                BundledPluginPackage(
                    byteArrayOf(1),
                    PluginArtifact(
                        pluginId.value,
                        "2.0.0",
                        "https://bundled.openstory.app/plugin.osp",
                        "0".repeat(64),
                    ),
                ),
            )
        }
        val provisioner = BundledPluginProvisioner(
            source,
            installer,
            PluginUpdateService(installer, state),
            state,
        )

        provisioner.ensureProvisioned()

        assertEquals("3.0.0", state.find(pluginId)?.activeVersion?.version)
    }
}
