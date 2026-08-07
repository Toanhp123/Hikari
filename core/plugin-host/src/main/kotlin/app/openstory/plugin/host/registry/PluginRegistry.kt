package app.openstory.plugin.host.registry

import app.openstory.common.AppResult

data class PluginActivation(
    val pluginId: String,
    val version: String,
    val packageSha256: String,
    val location: String,
    val signatureState: String,
    val signerKeyId: String?,
    val signerFingerprintSha256: String?,
    val installSource: String,
    val sourceReference: String,
    val unsignedWarningAcknowledged: Boolean,
    val acceptedCapabilities: Set<String>,
)

data class ActivatedPlugin(
    val pluginId: String,
    val version: String,
    val location: String,
    val enabled: Boolean,
)

data class PluginRegistration(
    val pluginId: String,
    val enabled: Boolean,
    val activeVersion: String?,
    val previousVersion: String?,
)

interface PluginRegistry {
    suspend fun find(
        pluginId: String,
    ): PluginRegistration?
}

interface MutablePluginRegistry : PluginRegistry {
    suspend fun activate(
        activation: PluginActivation,
    ): AppResult<ActivatedPlugin>

    suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): AppResult<Unit>
}
