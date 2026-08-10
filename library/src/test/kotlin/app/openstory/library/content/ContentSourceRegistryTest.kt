package app.openstory.library.content

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.PluginRuntime
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class ContentSourceRegistryTest {
    @Test
    fun enabledReusesSourceForSamePluginVersion() = runTest {
        val runtime = MutableEnabledRuntime(installed("1.0.0"))
        val registry = PluginContentSourceRegistry(runtime, Json)

        val first = registry.enabled().single()
        val second = registry.enabled().single()

        assertSame(first, second)
    }

    @Test
    fun versionChangeReplacesCachedSource() = runTest {
        val runtime = MutableEnabledRuntime(installed("1.0.0"))
        val registry = PluginContentSourceRegistry(runtime, Json)
        val first = registry.enabled().single()

        runtime.plugin = installed("2.0.0")
        val second = registry.enabled().single()

        assertNotSame(first, second)
    }
}

private fun installed(version: String) = InstalledPlugin(
    pluginId = PluginId("org.example.content"),
    version = version,
    services = setOf(PluginService.CONTENT),
    allowedNetworkHosts = setOf("reader.example"),
)

private class MutableEnabledRuntime(
    var plugin: InstalledPlugin,
) : PluginRuntime {
    override suspend fun enabled(service: PluginService): List<InstalledPlugin> = listOf(plugin)

    override suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> = error("unused")
}
