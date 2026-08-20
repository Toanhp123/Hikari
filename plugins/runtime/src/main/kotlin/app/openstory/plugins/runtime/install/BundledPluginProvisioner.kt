package app.openstory.plugins.runtime.install

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.update.PluginUpdateDecision
import app.openstory.plugins.runtime.update.PluginUpdateService
import app.openstory.plugins.runtime.update.compareVersions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BundledPluginProvisioner(
    private val source: BundledPluginSource,
    private val installer: PluginInstaller,
    private val updates: PluginUpdateService,
    private val state: PluginStateStore,
) {
    private val provisionMutex = Mutex()

    @Volatile
    private var provisioningSucceeded = false
    private var provisionInFlight: CompletableDeferred<Map<PluginId, PluginCallResult.Failure>>? = null

    suspend fun ensureProvisioned(): Map<PluginId, PluginCallResult.Failure> =
        if (provisioningSucceeded) {
            emptyMap()
        } else {
            var ownsProvisioning = false
            val inFlight = provisionMutex.withLock {
                if (provisioningSucceeded) {
                    null
                } else {
                    provisionInFlight ?: CompletableDeferred<Map<PluginId, PluginCallResult.Failure>>().also {
                        provisionInFlight = it
                        ownsProvisioning = true
                    }
                }
            }
            when {
                inFlight == null -> emptyMap()
                ownsProvisioning -> runProvisioning(inFlight)
                else -> inFlight.await()
            }
        }

    private suspend fun runProvisioning(
        inFlight: CompletableDeferred<Map<PluginId, PluginCallResult.Failure>>,
    ): Map<PluginId, PluginCallResult.Failure> {
        val result = runCatching { provision() }
        withContext(NonCancellable) {
            provisionMutex.withLock {
                provisionInFlight = null
                result.fold(
                    onSuccess = { failures ->
                        if (failures.isEmpty()) provisioningSucceeded = true
                        inFlight.complete(failures)
                    },
                    onFailure = inFlight::completeExceptionally,
                )
            }
        }
        return result.getOrThrow()
    }

    private suspend fun provision(): Map<PluginId, PluginCallResult.Failure> {
        val failures = linkedMapOf<PluginId, PluginCallResult.Failure>()
        for (packageValue in source.packages()) {
            val pluginId = PluginId(packageValue.provenance.pluginId)
            val installed = state.find(pluginId)
            val result: PluginCallResult<*> = when {
                installed == null -> installer.install(packageValue.bytes, packageValue.provenance)
                compareVersions(packageValue.provenance.version, installed.activeVersion.version) > 0 ->
                    updates.apply(packageValue.bytes, packageValue.provenance)
                else -> PluginCallResult.Success(Unit)
            }
            val failure = when {
                result is PluginCallResult.Failure -> result
                result.needsCapabilityReview() -> PluginCallResult.Failure("plugin.update_needs_review", false)
                else -> null
            }
            if (failure != null) failures[pluginId] = failure
        }
        return failures
    }
}

private fun PluginCallResult<*>.needsCapabilityReview(): Boolean =
    this is PluginCallResult.Success &&
        value is app.openstory.plugins.runtime.update.PluginUpdateResult &&
        value.decision == PluginUpdateDecision.NEEDS_REVIEW
