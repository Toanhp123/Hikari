package app.openstory.plugins.runtime

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginCapabilities
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginProtocolVersion
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.runtime.capabilities.CapabilityDispatcher
import app.openstory.plugins.runtime.execution.JavaScriptEngine
import app.openstory.plugins.runtime.execution.PluginOperationRunner
import app.openstory.plugins.runtime.install.BundledPluginProvisioner
import app.openstory.plugins.runtime.install.BundledPluginSource
import app.openstory.plugins.runtime.install.PackageVerifier
import app.openstory.plugins.runtime.install.PluginInstaller
import app.openstory.plugins.runtime.install.PluginPackageStorage
import app.openstory.plugins.runtime.install.VerifiedPluginPackage
import app.openstory.plugins.runtime.persistence.PluginDiagnosticEvent
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import app.openstory.plugins.runtime.update.PluginUpdateService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class DefaultPluginRuntimePerformanceTest {
    @Test
    fun `operation discovery and invocation honor manifest declarations`() = runTest {
        val pluginId = PluginId("org.example.plugin")
        val state = MutableStateStore(stored(pluginId, "1.0.0", "a".repeat(64)))
        val storage = CountingPackageStorage().apply {
            put(
                pluginId,
                "1.0.0",
                manifest("1.0.0", operations = setOf(PluginOperation.CATALOG_SEARCH)),
                "globalThis.openstoryPlugin = {};",
            )
        }
        val runtime = runtime(state, storage)

        assertEquals(listOf(pluginId), runtime.enabled(PluginOperation.CATALOG_SEARCH).map { it.pluginId })
        assertTrue(runtime.enabled(PluginOperation.CATALOG_HOME).isEmpty())
        val unavailable = assertIs<PluginCallResult.Failure>(
            runtime.invoke(pluginId, PluginOperation.CATALOG_HOME, JsonObject(emptyMap())),
        )
        assertEquals("plugin.operation_unavailable", unavailable.code)
    }

    @Test
    fun `active package is loaded once until immutable package identity changes`() = runTest {
        val pluginId = PluginId("org.example.plugin")
        val state = MutableStateStore(stored(pluginId, "1.0.0", "a".repeat(64)))
        val storage = CountingPackageStorage().apply {
            put(pluginId, "1.0.0", manifest("1.0.0"), "globalThis.openstoryPlugin = {};")
        }
        val runtime = runtime(state, storage)

        assertIs<PluginCallResult.Success<*>>(
            runtime.invoke(pluginId, PluginOperation.CATALOG_HOME, JsonObject(emptyMap())),
        )
        assertIs<PluginCallResult.Success<*>>(
            runtime.invoke(pluginId, PluginOperation.CATALOG_HOME, JsonObject(emptyMap())),
        )
        assertEquals(2, storage.readCalls)

        state.value = stored(pluginId, "1.0.0", "b".repeat(64))
        assertIs<PluginCallResult.Success<*>>(
            runtime.invoke(pluginId, PluginOperation.CATALOG_HOME, JsonObject(emptyMap())),
        )
        assertEquals(4, storage.readCalls)
    }

    @Test
    fun `concurrent invocations share one active package load`() = runTest {
        val pluginId = PluginId("org.example.plugin")
        val state = MutableStateStore(stored(pluginId, "1.0.0", "a".repeat(64)))
        val storage = CountingPackageStorage().apply {
            put(pluginId, "1.0.0", manifest("1.0.0"), "globalThis.openstoryPlugin = {};")
            blockFirstManifestRead = true
        }
        val runtime = runtime(state, storage)

        val first = async { runtime.invoke(pluginId, PluginOperation.CATALOG_HOME, JsonObject(emptyMap())) }
        storage.firstManifestRead.await()
        val second = async { runtime.invoke(pluginId, PluginOperation.CATALOG_HOME, JsonObject(emptyMap())) }
        yield()
        storage.releaseManifestRead.complete(Unit)

        assertIs<PluginCallResult.Success<*>>(first.await())
        assertIs<PluginCallResult.Success<*>>(second.await())
        assertEquals(2, storage.readCalls)
    }

    @Test
    fun `failed package load is not cached`() = runTest {
        val pluginId = PluginId("org.example.plugin")
        val state = MutableStateStore(stored(pluginId, "1.0.0", "a".repeat(64)))
        val storage = CountingPackageStorage().apply {
            put(pluginId, "1.0.0", manifest("1.0.0"), "globalThis.openstoryPlugin = {};")
            failManifestReads = true
        }
        val runtime = runtime(state, storage)

        assertIs<PluginCallResult.Failure>(
            runtime.invoke(pluginId, PluginOperation.CATALOG_HOME, JsonObject(emptyMap())),
        )
        storage.failManifestReads = false
        assertIs<PluginCallResult.Success<*>>(
            runtime.invoke(pluginId, PluginOperation.CATALOG_HOME, JsonObject(emptyMap())),
        )

        assertEquals(3, storage.readCalls)
    }

    private fun runtime(state: PluginStateStore, storage: PluginPackageStorage): DefaultPluginRuntime {
        val installer = PluginInstaller(PackageVerifier(), storage, state)
        val bundled = BundledPluginProvisioner(
            BundledPluginSource { emptyList() },
            installer,
            PluginUpdateService(installer, state),
            state,
        )
        val engine = JavaScriptEngine { _, _, _, _, _ -> "{\"sections\":[]}" }
        val runner = PluginOperationRunner(
            engine,
            CapabilityDispatcher { _, _, _, _, _ -> PluginCallResult.Failure("unused", false) },
            NoopDiagnostics,
        )
        return DefaultPluginRuntime(state, storage, runner, bundled, Json)
    }

    private fun manifest(
        version: String,
        operations: Set<PluginOperation>? = null,
    ) = PluginManifest(
        id = "org.example.plugin",
        name = "Example",
        version = version,
        protocol = PluginProtocolVersion(1),
        provides = setOf(PluginService.CATALOG),
        operations = operations,
        capabilities = PluginCapabilities(),
    )

    private fun stored(pluginId: PluginId, version: String, sha: String) = StoredPluginState(
        pluginId = pluginId,
        services = setOf(PluginService.CATALOG),
        enabled = true,
        activeVersion = StoredPluginVersion(version, "/packages/${pluginId.value}/$version", sha, null),
        previousVersion = null,
        acceptedNetworkHosts = emptySet(),
    )
}

private class MutableStateStore(var value: StoredPluginState) : PluginStateStore {
    override suspend fun find(pluginId: PluginId): StoredPluginState? = value.takeIf { it.pluginId == pluginId }
    override suspend fun all(): List<StoredPluginState> = listOf(value)
    override suspend fun replace(state: StoredPluginState) { value = state }
}

private class CountingPackageStorage : PluginPackageStorage {
    private val values = mutableMapOf<Pair<PluginId, String>, Map<String, ByteArray>>()
    var readCalls = 0
    var failManifestReads = false
    var blockFirstManifestRead = false
    val firstManifestRead = CompletableDeferred<Unit>()
    val releaseManifestRead = CompletableDeferred<Unit>()

    fun put(pluginId: PluginId, version: String, manifest: PluginManifest, script: String) {
        values[pluginId to version] = mapOf(
            "manifest.json" to Json.encodeToString(manifest).encodeToByteArray(),
            "main.js" to script.encodeToByteArray(),
        )
    }

    override suspend fun readEntry(
        pluginId: PluginId,
        version: String,
        entry: String,
    ): PluginCallResult<ByteArray> {
        readCalls++
        if (entry == "manifest.json" && blockFirstManifestRead && firstManifestRead.complete(Unit)) {
            releaseManifestRead.await()
        }
        if (entry == "manifest.json" && failManifestReads) {
            return PluginCallResult.Failure("plugin.package_entry_missing", false)
        }
        return values[pluginId to version]?.get(entry)?.let { PluginCallResult.Success(it) }
            ?: PluginCallResult.Failure("plugin.package_entry_missing", false)
    }

    override suspend fun store(value: VerifiedPluginPackage): PluginCallResult<StoredPluginVersion> =
        PluginCallResult.Failure("unused", false)

    override suspend fun remove(location: String) = Unit
}

private object NoopDiagnostics : PluginDiagnosticsSink {
    override suspend fun record(event: PluginDiagnosticEvent) = Unit
    override suspend fun recent(pluginId: PluginId, limit: Int): List<PluginDiagnosticEvent> = emptyList()
}
