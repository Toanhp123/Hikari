package app.openstory.database.repository

import app.openstory.database.dao.PluginStateDao
import app.openstory.database.entity.PluginStateEntity
import app.openstory.database.entity.PluginVersionEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginVersionRepositoryTest {
    @Test
    fun pinnedOperationKeepsVersionWhileSubsequentPinUsesNewActivation() = runTest {
        val dao = SwitchingPluginVersionDao()
        val repository = PluginVersionRepository(dao)

        val runningOperation = repository.pinActive(PLUGIN_ID)
        dao.activate("2.0.0")
        val subsequentOperation = repository.pinActive(PLUGIN_ID)

        assertEquals("1.0.0", runningOperation?.version)
        assertEquals("plugins/$PLUGIN_ID/1.0.0", runningOperation?.location)
        assertEquals("2.0.0", subsequentOperation?.version)
        assertEquals("plugins/$PLUGIN_ID/2.0.0", subsequentOperation?.location)
    }
}

private class SwitchingPluginVersionDao : PluginStateDao() {
    private var state = state("1.0.0", null)
    private val versions = mutableMapOf(
        "1.0.0" to version("1.0.0"),
        "2.0.0" to version("2.0.0"),
    )

    fun activate(version: String) {
        state = state(version, state.activeVersion)
    }

    override suspend fun find(pluginId: String): PluginStateEntity? =
        state.takeIf { it.pluginId == pluginId }

    override suspend fun findVersion(
        pluginId: String,
        version: String,
    ): PluginVersionEntity? = versions[version]?.takeIf { it.pluginId == pluginId }

    override suspend fun insertVersionIfMissing(version: PluginVersionEntity): Long =
        error("Pinning must not write versions.")

    override suspend fun upsert(state: PluginStateEntity) {
        error("Pinning must not update plugin state.")
    }

    override suspend fun updateEnabled(
        pluginId: String,
        enabled: Boolean,
        updatedAtEpochMillis: Long,
    ): Int = error("Pinning must not change enabled state.")
}

private fun state(
    activeVersion: String,
    previousVersion: String?,
): PluginStateEntity = PluginStateEntity(
    pluginId = PLUGIN_ID,
    enabled = true,
    activeVersion = activeVersion,
    previousVersion = previousVersion,
    updatedAtEpochMillis = 1L,
)

private fun version(version: String): PluginVersionEntity = PluginVersionEntity(
    pluginId = PLUGIN_ID,
    version = version,
    packageSha256 = version.first().toString().repeat(64),
    location = "plugins/$PLUGIN_ID/$version",
    trustSignatureState = "UNSIGNED",
    signerKeyId = null,
    signerFingerprintSha256 = null,
    installSource = "LOCAL_FILE",
    sourceReference = "fixture-$version.osp",
    unsignedWarningAcknowledged = true,
    acceptedCapabilities = "",
    installedAtEpochMillis = 1L,
)

private const val PLUGIN_ID = "community.fixture"
