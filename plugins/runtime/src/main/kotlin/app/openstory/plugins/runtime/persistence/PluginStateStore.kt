package app.openstory.plugins.runtime.persistence

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService

data class StoredPluginVersion(
    val version: String,
    val packageLocation: String,
    val sha256: String,
    val signerFingerprint: String?,
) {
    init {
        require(version.isNotBlank()) { "Stored version must not be blank" }
        require(packageLocation.isNotBlank()) { "Package location must not be blank" }
        require(SHA256.matches(sha256)) { "Stored SHA-256 is invalid" }
        require(signerFingerprint == null || signerFingerprint.isNotBlank()) {
            "Signer fingerprint must be null or non-blank"
        }
    }

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

data class StoredPluginState(
    val pluginId: PluginId,
    val services: Set<PluginService>,
    val enabled: Boolean,
    val activeVersion: StoredPluginVersion,
    val previousVersion: StoredPluginVersion?,
    val acceptedNetworkHosts: Set<String>,
) {
    init {
        require(services.isNotEmpty()) { "Stored plugin must provide a service" }
        require(previousVersion?.version != activeVersion.version) {
            "Previous version must differ from active version"
        }
    }
}

interface PluginStateStore {
    suspend fun find(pluginId: PluginId): StoredPluginState?
    suspend fun all(): List<StoredPluginState>
    suspend fun replace(state: StoredPluginState)
}
