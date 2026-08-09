package app.openstory.database.repository

import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.common.id.PluginId
import app.openstory.database.OpenStoryDatabase
import app.openstory.database.dao.PluginStateDao
import app.openstory.database.entity.PluginStateEntity
import app.openstory.database.entity.PluginVersionEntity
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomPluginStateStore internal constructor(
    private val dao: PluginStateDao,
    private val clock: Clock = SystemClock,
    private val json: Json = Json,
) : PluginStateStore {
    constructor(database: OpenStoryDatabase) : this(database.pluginStateDao())

    override suspend fun find(pluginId: PluginId): StoredPluginState? = dao.find(pluginId.value)?.toStored()

    override suspend fun all(): List<StoredPluginState> = dao.all().mapNotNull { entity -> entity.toStored() }

    override suspend fun replace(state: StoredPluginState) {
        dao.insertVersionIfMissing(state.activeVersion.toEntity(state))
        state.previousVersion?.let { dao.insertVersionIfMissing(it.toEntity(state)) }
        dao.upsert(
            PluginStateEntity(
                pluginId = state.pluginId.value,
                enabled = state.enabled,
                activeVersion = state.activeVersion.version,
                previousVersion = state.previousVersion?.version,
                updatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
    }

    private suspend fun PluginStateEntity.toStored(): StoredPluginState? = activeVersion
        ?.let { version -> dao.findVersion(pluginId, version) }
        ?.let { active ->
            runCatching {
                json.decodeFromString<RuntimePluginMetadata>(active.acceptedCapabilities)
            }.getOrNull()?.let { metadata ->
                StoredPluginState(
                    pluginId = PluginId(pluginId),
                    services = metadata.services.map(PluginService::valueOf).toSet(),
                    enabled = enabled,
                    activeVersion = active.toStoredVersion(),
                    previousVersion = previousVersion?.let { version ->
                        dao.findVersion(pluginId, version)?.toStoredVersion()
                    },
                    acceptedNetworkHosts = metadata.acceptedNetworkHosts,
                )
            }
        }

    private fun StoredPluginVersion.toEntity(state: StoredPluginState) = PluginVersionEntity(
        pluginId = state.pluginId.value,
        version = version,
        packageSha256 = sha256,
        location = packageLocation,
        trustSignatureState = if (signerFingerprint == null) "UNSIGNED" else "VERIFIED",
        signerKeyId = null,
        signerFingerprintSha256 = signerFingerprint,
        installSource = "RUNTIME",
        sourceReference = packageLocation,
        unsignedWarningAcknowledged = signerFingerprint == null,
        acceptedCapabilities = json.encodeToString(
            RuntimePluginMetadata(
                services = state.services.map(PluginService::name).toSet(),
                acceptedNetworkHosts = state.acceptedNetworkHosts,
            ),
        ),
        installedAtEpochMillis = clock.nowEpochMillis(),
    )
}

private fun PluginVersionEntity.toStoredVersion() = StoredPluginVersion(
    version = version,
    packageLocation = location,
    sha256 = packageSha256,
    signerFingerprint = signerFingerprintSha256,
)

@Serializable
private data class RuntimePluginMetadata(
    val services: Set<String>,
    val acceptedNetworkHosts: Set<String>,
)
