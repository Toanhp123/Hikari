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
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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

    @Test
    fun staleRuntimeCompletionDoesNotEvictNewerCachedSource() = runTest {
        val runtime = OutOfOrderVersionRuntime()
        val registry = PluginContentSourceRegistry(runtime, Json)

        val first = async { registry.enabled() }
        runtime.firstEntered.await()
        val second = async { registry.enabled() }
        val newer = second.await().single()
        runtime.releaseFirst.complete(Unit)
        first.await()

        val latest = registry.enabled().single()

        assertSame(newer, latest)
    }

    @Test
    fun runtimeLookupDoesNotHoldSourceCacheMutex() = runTest {
        val runtime = FirstCallBlockingRuntime(installed("1.0.0"))
        val registry = PluginContentSourceRegistry(runtime, Json)

        val first = async { registry.enabled() }
        runtime.firstEntered.await()
        val second = async { registry.enabled() }

        withTimeout(1_000) { second.await() }
        assertTrue(runtime.calls >= 2)
        runtime.releaseFirst.complete(Unit)
        first.await()
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

private class OutOfOrderVersionRuntime : PluginRuntime {
    val firstEntered = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    var calls = 0

    override suspend fun enabled(service: PluginService): List<InstalledPlugin> {
        calls++
        return if (calls == 1) {
            firstEntered.complete(Unit)
            releaseFirst.await()
            listOf(installed("1.0.0"))
        } else {
            listOf(installed("2.0.0"))
        }
    }

    override suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> = error("unused")
}

private class FirstCallBlockingRuntime(
    private val plugin: InstalledPlugin,
) : PluginRuntime {
    val firstEntered = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    var calls = 0

    override suspend fun enabled(service: PluginService): List<InstalledPlugin> {
        calls++
        if (calls == 1) {
            firstEntered.complete(Unit)
            releaseFirst.await()
        }
        return listOf(plugin)
    }

    override suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> = error("unused")
}
