package app.openstory.plugins.runtime

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement

class PluginRuntimeContractTest {
    @Test
    fun enabledCatalogExcludesDisabledAndContentOnlyPlugins() = runTest {
        val states = listOf(
            storedState("org.example.catalog", enabled = true, services = setOf(PluginService.CATALOG)),
            storedState("org.example.disabled", enabled = false, services = setOf(PluginService.CATALOG)),
            storedState("org.example.content", enabled = true, services = setOf(PluginService.CONTENT)),
        )
        val runtime = stateOnlyRuntime(FakePluginStateStore(states))

        assertEquals(
            listOf(PluginId("org.example.catalog")),
            runtime.enabled(PluginService.CATALOG).map { it.pluginId },
        )
        assertEquals(
            setOf("api.example.com"),
            runtime.enabled(PluginService.CATALOG).single().allowedNetworkHosts,
        )
    }

    @Test
    fun failureStringContainsOnlySafeFields() {
        val failure = PluginCallResult.Failure(
            code = "plugin.execution_failed",
            retryable = false,
            safeDetail = "operation failed",
        )
        val rendered = failure.toString()
        assertFalse("secret-cookie" in rendered)
        assertFalse("https://source.example/path?q=secret" in rendered)
        assertTrue("plugin.execution_failed" in rendered)
    }
}

private class FakePluginStateStore(states: List<StoredPluginState>) : PluginStateStore {
    private val values = states.associateBy(StoredPluginState::pluginId).toMutableMap()

    override suspend fun find(pluginId: PluginId): StoredPluginState? = values[pluginId]
    override suspend fun all(): List<StoredPluginState> = values.values.toList()
    override suspend fun replace(state: StoredPluginState) {
        values[state.pluginId] = state
    }
}

private fun stateOnlyRuntime(state: PluginStateStore): PluginRuntime = object : PluginRuntime {
    override suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> = PluginCallResult.Failure("plugin.execution_unavailable", false)

    override suspend fun enabled(service: PluginService): List<InstalledPlugin> =
        state.all().filter { it.enabled && service in it.services }.map {
            InstalledPlugin(it.pluginId, it.activeVersion.version, it.services, it.acceptedNetworkHosts)
        }.sortedBy { it.pluginId.value }
}

private fun storedState(
    pluginId: String,
    enabled: Boolean,
    services: Set<PluginService>,
) = StoredPluginState(
    pluginId = PluginId(pluginId),
    services = services,
    enabled = enabled,
    activeVersion = StoredPluginVersion("1.0.0", "/packages/$pluginId/1.0.0", "a".repeat(64), null),
    previousVersion = null,
    acceptedNetworkHosts = setOf("api.example.com"),
)
