package app.openstory.storage.room.plugins

import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import app.openstory.storage.room.OpenStoryDatabase

class RoomPluginStateStore internal constructor(
    private val dao: PluginStateDao,
    private val clock: Clock = SystemClock,
) : PluginStateStore {
    constructor(database: OpenStoryDatabase) : this(database.pluginStateDao())

    override suspend fun find(pluginId: PluginId): StoredPluginState? = dao.find(pluginId.value)?.toStored()

    override suspend fun all(): List<StoredPluginState> = dao.all().mapNotNull { it.toStored() }

    override suspend fun replace(state: StoredPluginState) {
        val now = clock.nowEpochMillis()
        val active = state.activeVersion.resolveEntity(state, now)
        val previous = state.previousVersion?.let { version ->
            version.resolveEntity(state, now)
        }
        dao.replace(
            PluginStateEntity(
                pluginId = state.pluginId.value,
                enabled = state.enabled,
                activeVersion = state.activeVersion.version,
                previousVersion = state.previousVersion?.version,
                updatedAtEpochMillis = now,
            ),
            listOfNotNull(active, previous),
        )
    }

    private suspend fun PluginStateEntity.toStored(): StoredPluginState? {
        val active = dao.findVersion(pluginId, activeVersion) ?: return null
        val previous = previousVersion?.let { dao.findVersion(pluginId, it) }
        return StoredPluginState(
            pluginId = PluginId(pluginId),
            services = active.services.map(PluginService::valueOf).toSet(),
            enabled = enabled,
            activeVersion = active.toStoredVersion(),
            previousVersion = previous?.toStoredVersion(),
            acceptedNetworkHosts = active.acceptedNetworkHosts,
        )
    }

    private fun StoredPluginVersion.toEntity(
        state: StoredPluginState,
        installedAt: Long,
    ) = PluginVersionEntity(
        pluginId = state.pluginId.value,
        version = version,
        packageLocation = packageLocation,
        sha256 = sha256,
        signerFingerprint = signerFingerprint,
        services = state.services.map(PluginService::name).toSet(),
        acceptedNetworkHosts = state.acceptedNetworkHosts,
        installedAtEpochMillis = installedAt,
    )

    private suspend fun StoredPluginVersion.resolveEntity(
        state: StoredPluginState,
        installedAt: Long,
    ): PluginVersionEntity {
        val existing = dao.findVersion(state.pluginId.value, version)
            ?: return toEntity(state, installedAt)
        check(existing.toStoredVersion() == this) {
            "Installed plugin version provenance is immutable."
        }
        return existing
    }
}

private fun PluginVersionEntity.toStoredVersion() = StoredPluginVersion(
    version = version,
    packageLocation = packageLocation,
    sha256 = sha256,
    signerFingerprint = signerFingerprint,
)
