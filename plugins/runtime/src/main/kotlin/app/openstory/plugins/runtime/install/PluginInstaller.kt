package app.openstory.plugins.runtime.install

import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.persistence.StoredPluginState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class PluginInstaller(
    private val verifier: PackageVerifier,
    private val storage: PluginPackageStorage,
    private val state: PluginStateStore,
) {
    fun verify(
        packageBytes: ByteArray,
        provenance: PluginArtifact,
    ): PluginCallResult<VerifiedPluginPackage> = verifier.verify(packageBytes, provenance)

    suspend fun install(
        packageBytes: ByteArray,
        provenance: PluginArtifact,
        enabled: Boolean = true,
    ): PluginCallResult<StoredPluginState> {
        val verified = when (val result = verify(packageBytes, provenance)) {
            is PluginCallResult.Failure -> return result
            is PluginCallResult.Success -> result.value
        }
        return installVerified(verified, enabled)
    }

    suspend fun installVerified(
        verified: VerifiedPluginPackage,
        enabled: Boolean = true,
    ): PluginCallResult<StoredPluginState> {
        val stored = when (val result = storage.store(verified)) {
            is PluginCallResult.Failure -> return result
            is PluginCallResult.Success -> result.value
        }
        return try {
            val previous = state.find(verified.pluginId)
            val replacement = StoredPluginState(
                pluginId = verified.pluginId,
                services = verified.manifest.provides,
                enabled = previous?.enabled ?: enabled,
                activeVersion = stored,
                previousVersion = previous?.activeVersion,
                acceptedNetworkHosts = verified.manifest.capabilities.network?.hosts.orEmpty(),
            )
            state.replace(replacement)
            PluginCallResult.Success(replacement)
        } catch (failure: CancellationException) {
            withContext(NonCancellable) { storage.remove(stored.packageLocation) }
            throw failure
        } catch (_: RuntimeException) {
            storage.remove(stored.packageLocation)
            PluginCallResult.Failure("plugin.activation_failed", retryable = true)
        }
    }
}
