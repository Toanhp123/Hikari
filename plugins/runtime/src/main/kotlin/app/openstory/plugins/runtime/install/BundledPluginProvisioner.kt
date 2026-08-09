package app.openstory.plugins.runtime.install

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.update.PluginUpdateDecision
import app.openstory.plugins.runtime.update.PluginUpdateService
import app.openstory.plugins.runtime.update.compareVersions

class BundledPluginProvisioner(
    private val source: BundledPluginSource,
    private val installer: PluginInstaller,
    private val updates: PluginUpdateService,
    private val state: PluginStateStore,
) {
    suspend fun ensureProvisioned(): PluginCallResult<Unit> {
        var failure: PluginCallResult.Failure? = null
        for (packageValue in source.packages()) {
            val pluginId = PluginId(packageValue.provenance.pluginId)
            val installed = state.find(pluginId)
            val result: PluginCallResult<*> = when {
                installed == null -> installer.install(packageValue.bytes, packageValue.provenance)
                compareVersions(packageValue.provenance.version, installed.activeVersion.version) > 0 ->
                    updates.apply(packageValue.bytes, packageValue.provenance)
                else -> PluginCallResult.Success(Unit)
            }
            failure = when {
                result is PluginCallResult.Failure -> result
                result.needsCapabilityReview() -> PluginCallResult.Failure("plugin.update_needs_review", false)
                else -> null
            }
            if (failure != null) break
        }
        return failure ?: PluginCallResult.Success(Unit)
    }
}

private fun PluginCallResult<*>.needsCapabilityReview(): Boolean =
    this is PluginCallResult.Success &&
        value is app.openstory.plugins.runtime.update.PluginUpdateResult &&
        value.decision == PluginUpdateDecision.NEEDS_REVIEW
