package app.openstory.plugins.runtime.update

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.install.PluginInstaller
import app.openstory.plugins.runtime.persistence.PluginStateStore

enum class PluginUpdateDecision { APPLIED, NEEDS_REVIEW, NOT_NEWER }

data class PluginUpdateResult(val decision: PluginUpdateDecision)

class PluginUpdateService(
    private val installer: PluginInstaller,
    private val state: PluginStateStore,
) {
    suspend fun apply(
        packageBytes: ByteArray,
        provenance: PluginArtifact,
        approveCapabilityExpansion: Boolean = false,
    ): PluginCallResult<PluginUpdateResult> {
        val current = state.find(PluginId(provenance.pluginId))
        return if (current != null && compareVersions(provenance.version, current.activeVersion.version) <= 0) {
            PluginCallResult.Success(PluginUpdateResult(PluginUpdateDecision.NOT_NEWER))
        } else {
            applyVerified(packageBytes, provenance, current, approveCapabilityExpansion)
        }
    }

    private suspend fun applyVerified(
        packageBytes: ByteArray,
        provenance: PluginArtifact,
        current: app.openstory.plugins.runtime.persistence.StoredPluginState?,
        approveCapabilityExpansion: Boolean,
    ): PluginCallResult<PluginUpdateResult> = when (val result = installer.verify(packageBytes, provenance)) {
        is PluginCallResult.Failure -> result
        is PluginCallResult.Success -> installCandidate(result.value, current, approveCapabilityExpansion)
    }

    private suspend fun installCandidate(
        verified: app.openstory.plugins.runtime.install.VerifiedPluginPackage,
        current: app.openstory.plugins.runtime.persistence.StoredPluginState?,
        approveCapabilityExpansion: Boolean,
    ): PluginCallResult<PluginUpdateResult> {
        val candidateHosts = verified.manifest.capabilities.network?.hosts.orEmpty()
        val expandsHosts = current != null && !current.acceptedNetworkHosts.containsAll(candidateHosts)
        return if (expandsHosts && !approveCapabilityExpansion) {
            PluginCallResult.Success(PluginUpdateResult(PluginUpdateDecision.NEEDS_REVIEW))
        } else {
            installVerified(verified)
        }
    }

    private suspend fun installVerified(
        verified: app.openstory.plugins.runtime.install.VerifiedPluginPackage,
    ): PluginCallResult<PluginUpdateResult> = when (val result = installer.installVerified(verified)) {
            is PluginCallResult.Failure -> result
            is PluginCallResult.Success ->
                PluginCallResult.Success(PluginUpdateResult(PluginUpdateDecision.APPLIED))
    }
}

internal fun compareVersions(left: String, right: String): Int {
    val leftParts = left.substringBefore('-').split('.').map(String::toInt)
    val rightParts = right.substringBefore('-').split('.').map(String::toInt)
    return leftParts.zip(rightParts).firstOrNull { it.first != it.second }
        ?.let { (a, b) -> a.compareTo(b) } ?: left.compareTo(right)
}
