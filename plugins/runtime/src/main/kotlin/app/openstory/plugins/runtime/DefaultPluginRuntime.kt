package app.openstory.plugins.runtime

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.runtime.execution.PluginOperationRunner
import app.openstory.plugins.runtime.install.BundledPluginProvisioner
import app.openstory.plugins.runtime.install.PluginPackageStorage
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.persistence.StoredPluginState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class DefaultPluginRuntime(
    private val state: PluginStateStore,
    private val storage: PluginPackageStorage,
    private val runner: PluginOperationRunner,
    private val bundled: BundledPluginProvisioner,
    private val json: Json = Json,
) : PluginRuntime {
    private val loadedPackages = ConcurrentHashMap<PluginId, CachedPluginPackage>()
    private val packageLocks = ConcurrentHashMap<PluginId, Mutex>()

    override suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> =
        bundled.ensureProvisioned()[pluginId] ?: invokeInstalled(pluginId, operation, input)

    private suspend fun invokeInstalled(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> = when (val stored = state.find(pluginId)) {
        null -> PluginCallResult.Failure("plugin.not_installed", false)
        else -> invokeStored(stored, operation, input)
    }

    override suspend fun enabled(service: PluginService): List<InstalledPlugin> {
        bundled.ensureProvisioned()
        return state.all()
            .filter { stored -> stored.enabled && service in stored.services }
            .map(StoredPluginState::toInstalledPlugin)
            .sortedBy { plugin -> plugin.pluginId.value }
    }

    override suspend fun enabled(operation: PluginOperation): List<InstalledPlugin> {
        bundled.ensureProvisioned()
        return state.all()
            .filter { stored -> stored.enabled && operation.service in stored.services }
            .mapNotNull { stored ->
                when (val loaded = loadPackage(stored)) {
                    is PluginCallResult.Failure -> null
                    is PluginCallResult.Success -> stored
                        .takeIf { loaded.value.manifest.supports(operation) }
                        ?.toInstalledPlugin(loaded.value.manifest)
                }
            }
            .sortedBy { plugin -> plugin.pluginId.value }
    }

    private suspend fun invokeStored(
        stored: StoredPluginState,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> {
        if (!stored.enabled) return PluginCallResult.Failure("plugin.disabled", false)
        return when (val loaded = loadPackage(stored)) {
            is PluginCallResult.Failure -> loaded
            is PluginCallResult.Success -> if (!loaded.value.manifest.supports(operation)) {
                PluginCallResult.Failure("plugin.operation_unavailable", false)
            } else {
                runner.run(
                    stored.pluginId,
                    loaded.value.manifest,
                    loaded.value.script,
                    operation,
                    input,
                )
            }
        }
    }

    private suspend fun loadPackage(stored: StoredPluginState): PluginCallResult<LoadedPluginPackage> {
        val identity = stored.packageIdentity()
        loadedPackages.cached(identity)?.let { return PluginCallResult.Success(it) }
        val lock = packageLocks.computeIfAbsent(stored.pluginId) { Mutex() }
        return lock.withLock {
            loadedPackages.cached(identity)?.let { return@withLock PluginCallResult.Success(it) }
            when (val loaded = readPackage(stored)) {
                is PluginCallResult.Failure -> loaded
                is PluginCallResult.Success -> {
                    loadedPackages[stored.pluginId] = CachedPluginPackage(identity, loaded.value)
                    loaded
                }
            }
        }
    }

    private suspend fun readPackage(stored: StoredPluginState): PluginCallResult<LoadedPluginPackage> {
        val version = stored.activeVersion.version
        val manifestBytes = storage.readEntry(stored.pluginId, version, MANIFEST_ENTRY).valueOrNull()
        val manifest = manifestBytes?.let(::decodeManifest)
        return when {
            manifestBytes == null -> storageFailure()
            manifest == null -> PluginCallResult.Failure("plugin.manifest_invalid", false)
            else -> {
                val scriptBytes = storage.readEntry(stored.pluginId, version, MAIN_SCRIPT_ENTRY).valueOrNull()
                if (scriptBytes == null) {
                    storageFailure()
                } else {
                    PluginCallResult.Success(LoadedPluginPackage(manifest, scriptBytes.decodeToString()))
                }
            }
        }
    }

    private fun decodeManifest(bytes: ByteArray): PluginManifest? = runCatching {
        json.decodeFromString(PluginManifest.serializer(), bytes.decodeToString())
    }.getOrNull()

    private fun storageFailure(): PluginCallResult.Failure =
        PluginCallResult.Failure("plugin.package_entry_missing", false)

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val MAIN_SCRIPT_ENTRY = "main.js"
    }
}

private data class PluginPackageIdentity(
    val pluginId: PluginId,
    val version: String,
    val packageLocation: String,
    val sha256: String,
)

private data class LoadedPluginPackage(
    val manifest: PluginManifest,
    val script: String,
)

private data class CachedPluginPackage(
    val identity: PluginPackageIdentity,
    val value: LoadedPluginPackage,
)

private fun Map<PluginId, CachedPluginPackage>.cached(identity: PluginPackageIdentity): LoadedPluginPackage? =
    get(identity.pluginId)?.takeIf { cached -> cached.identity == identity }?.value

private fun StoredPluginState.toInstalledPlugin(manifest: PluginManifest? = null) = InstalledPlugin(
    pluginId = pluginId,
    version = activeVersion.version,
    services = services,
    allowedNetworkHosts = acceptedNetworkHosts,
    readerCapability = manifest?.capabilities?.reader,
)

private fun StoredPluginState.packageIdentity() = PluginPackageIdentity(
    pluginId = pluginId,
    version = activeVersion.version,
    packageLocation = activeVersion.packageLocation,
    sha256 = activeVersion.sha256,
)

private fun PluginCallResult<ByteArray>.valueOrNull(): ByteArray? =
    (this as? PluginCallResult.Success)?.value
