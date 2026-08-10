package app.openstory.plugins.runtime.update

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.install.MemoryPackageStorage
import app.openstory.plugins.runtime.install.MemoryStateStore
import app.openstory.plugins.runtime.install.PackageVerifier
import app.openstory.plugins.runtime.install.PluginInstaller
import app.openstory.plugins.runtime.install.packageBytes
import app.openstory.plugins.runtime.install.sha256
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class PluginUpdateServiceTest {
    @Test
    fun capabilityExpansionRequiresReviewWithoutMutatingState() = runTest {
        val pluginId = PluginId("org.example.plugin")
        val state = MemoryStateStore(
            listOf(
                StoredPluginState(
                    pluginId,
                    setOf(PluginService.CATALOG),
                    true,
                    StoredPluginVersion("1.0.0", "/1", "a".repeat(64), null),
                    null,
                    emptySet(),
                ),
            ),
        )
        val bytes = packageBytes(version = "2.0.0")
        val service = PluginUpdateService(
            PluginInstaller(PackageVerifier(), MemoryPackageStorage(), state),
            state,
        )
        val provenance = PluginArtifact(
            pluginId.value,
            "2.0.0",
            "https://plugins.example/plugin.osp",
            sha256(bytes),
        )

        val result = assertIs<PluginCallResult.Success<PluginUpdateResult>>(
            service.apply(bytes, provenance),
        )

        assertEquals(PluginUpdateDecision.NEEDS_REVIEW, result.value.decision)
        assertEquals("1.0.0", state.find(pluginId)?.activeVersion?.version)
    }
}
