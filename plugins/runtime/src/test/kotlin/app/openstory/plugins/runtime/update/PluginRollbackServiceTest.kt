package app.openstory.plugins.runtime.update

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.install.MemoryStateStore
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PluginRollbackServiceTest {
    @Test
    fun rollbackRestoresPreviousImmutableVersion() = runTest {
        val pluginId = PluginId("org.example.plugin")
        val state = MemoryStateStore(
            listOf(
                StoredPluginState(
                    pluginId,
                    setOf(PluginService.CATALOG),
                    true,
                    version("2.0.0"),
                    version("1.0.0"),
                    setOf("api.example.com"),
                ),
            ),
        )

        PluginRollbackService(state).rollback(pluginId)

        assertEquals("1.0.0", state.find(pluginId)?.activeVersion?.version)
    }

    private fun version(value: String) = StoredPluginVersion(value, "/$value", "a".repeat(64), null)
}
