package app.openstory.database.repository

import app.openstory.common.id.PluginId
import app.openstory.database.dao.PluginStateDao
import app.openstory.database.entity.PluginStateEntity
import app.openstory.database.entity.PluginVersionEntity
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomPluginStateStoreTest {
    @Test
    fun activeAndPreviousVersionsRoundTripThroughRuntimeSpi() = runTest {
        val dao = RecordingPluginStateDao()
        val repository = RoomPluginStateStore(dao)
        val state = storedPluginState(active = "2.0.0", previous = "1.0.0", enabled = true)

        repository.replace(state)

        assertEquals(state, repository.find(state.pluginId))
    }
}

private fun storedPluginState(active: String, previous: String?, enabled: Boolean) = StoredPluginState(
    pluginId = PluginId("org.example.plugin"),
    services = setOf(PluginService.CATALOG),
    enabled = enabled,
    activeVersion = storedVersion(active),
    previousVersion = previous?.let(::storedVersion),
    acceptedNetworkHosts = setOf("api.example.com"),
)

private fun storedVersion(version: String) = StoredPluginVersion(
    version = version,
    packageLocation = "plugins/org.example.plugin/$version",
    sha256 = "a".repeat(64),
    signerFingerprint = null,
)

private class RecordingPluginStateDao : PluginStateDao() {
    private val states = linkedMapOf<String, PluginStateEntity>()
    private val versions = linkedMapOf<Pair<String, String>, PluginVersionEntity>()

    override suspend fun find(pluginId: String) = states[pluginId]
    override suspend fun all() = states.values.toList()
    override suspend fun findVersion(pluginId: String, version: String) = versions[pluginId to version]
    override suspend fun insertVersionIfMissing(version: PluginVersionEntity): Long {
        val key = version.pluginId to version.version
        if (versions.putIfAbsent(key, version) != null) return -1L
        return 1L
    }
    override suspend fun upsert(state: PluginStateEntity) { states[state.pluginId] = state }
    override suspend fun updateEnabled(pluginId: String, enabled: Boolean, updatedAtEpochMillis: Long): Int = 0
}
