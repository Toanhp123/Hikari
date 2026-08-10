package app.openstory.plugins.runtime

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.runtime.execution.PluginOperationRunner
import app.openstory.plugins.runtime.install.BundledPluginProvisioner
import app.openstory.plugins.runtime.install.PluginPackageStorage
import app.openstory.plugins.runtime.persistence.PluginStateStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class DefaultPluginRuntime(
    private val state: PluginStateStore,
    private val storage: PluginPackageStorage,
    private val runner: PluginOperationRunner,
    private val bundled: BundledPluginProvisioner,
    private val json: Json = Json,
) : PluginRuntime {
    override suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> = when (val provisioned = bundled.ensureProvisioned()) {
        is PluginCallResult.Failure -> provisioned
        is PluginCallResult.Success -> invokeInstalled(pluginId, operation, input)
    }

    private suspend fun invokeInstalled(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> = when (val stored = state.find(pluginId)) {
        null -> PluginCallResult.Failure("plugin.not_installed", false)
        else -> invokeStored(stored, operation, input)
    }

    override suspend fun enabled(service: PluginService): List<InstalledPlugin> {
        if (bundled.ensureProvisioned() is PluginCallResult.Failure) return emptyList()
        return state.all().filter { it.enabled && service in it.services }.map {
            InstalledPlugin(it.pluginId, it.activeVersion.version, it.services, it.acceptedNetworkHosts)
        }.sortedBy { it.pluginId.value }
    }

    private suspend fun invokeStored(
        stored: app.openstory.plugins.runtime.persistence.StoredPluginState,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> {
        if (!stored.enabled) return PluginCallResult.Failure("plugin.disabled", false)
        val manifest = storage.readEntry(stored.pluginId, stored.activeVersion.version, "manifest.json")
        val script = storage.readEntry(stored.pluginId, stored.activeVersion.version, "main.js")
        return invokeLoaded(stored.pluginId, manifest, script, operation, input)
    }

    private suspend fun invokeLoaded(
        pluginId: PluginId,
        manifestBytes: PluginCallResult<ByteArray>,
        scriptBytes: PluginCallResult<ByteArray>,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> {
        val manifestValue = manifestBytes.valueOrNull()
        val scriptValue = scriptBytes.valueOrNull()
        return when {
            manifestValue == null || scriptValue == null -> storageFailure()
            else -> decodeManifest(manifestValue)?.let { manifest ->
                runner.run(pluginId, manifest, scriptValue.decodeToString(), operation, input)
            } ?: PluginCallResult.Failure("plugin.manifest_invalid", false)
        }
    }

    private fun decodeManifest(bytes: ByteArray): PluginManifest? = runCatching {
        json.decodeFromString(PluginManifest.serializer(), bytes.decodeToString())
    }.getOrNull()

    private fun storageFailure(): PluginCallResult.Failure =
        PluginCallResult.Failure("plugin.package_entry_missing", false)
}

private fun PluginCallResult<ByteArray>.valueOrNull(): ByteArray? =
    (this as? PluginCallResult.Success)?.value
