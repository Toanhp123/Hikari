package app.openstory.plugins.runtime.install

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class PluginInstallerTest {
    @Test
    fun checksumMismatchLeavesStateUntouched() = runTest {
        val state = MemoryStateStore()
        val storage = MemoryPackageStorage()
        val installer = PluginInstaller(PackageVerifier(), storage, state)
        val bytes = packageBytes()
        val provenance = PluginArtifact(
            "org.example.plugin",
            "1.0.0",
            "https://plugins.example/plugin.osp",
            "0".repeat(64),
        )

        assertIs<PluginCallResult.Failure>(installer.install(bytes, provenance))
        assertNull(state.find(PluginId("org.example.plugin")))
        assertEquals(0, storage.storeCalls)
    }
}

internal class MemoryStateStore(initial: List<StoredPluginState> = emptyList()) : PluginStateStore {
    private val values = initial.associateBy(StoredPluginState::pluginId).toMutableMap()
    override suspend fun find(pluginId: PluginId): StoredPluginState? = values[pluginId]
    override suspend fun all(): List<StoredPluginState> = values.values.toList()
    override suspend fun replace(state: StoredPluginState) {
        values[state.pluginId] = state
    }
}

internal class MemoryPackageStorage : PluginPackageStorage {
    var storeCalls = 0
    private val entries = mutableMapOf<Pair<PluginId, String>, Map<String, ByteArray>>()

    override suspend fun store(value: VerifiedPluginPackage): PluginCallResult<StoredPluginVersion> {
        storeCalls++
        entries[value.pluginId to value.version] = value.entries
        return PluginCallResult.Success(
            StoredPluginVersion(value.version, "/${value.pluginId.value}/${value.version}", value.sha256, null),
        )
    }

    override suspend fun readEntry(
        pluginId: PluginId,
        version: String,
        entry: String,
    ): PluginCallResult<ByteArray> = entries[pluginId to version]?.get(entry)?.let {
        PluginCallResult.Success(it)
    }
        ?: PluginCallResult.Failure("plugin.package_entry_missing", false)

    override suspend fun remove(location: String) = Unit
}
