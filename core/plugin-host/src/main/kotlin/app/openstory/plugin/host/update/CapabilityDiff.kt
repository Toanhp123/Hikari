package app.openstory.plugin.host.update

import app.openstory.plugin.api.PluginManifest

data class CapabilityDiff(
    val addedHosts: Set<String>,
    val removedHosts: Set<String>,
    val addedCapabilities: Set<String>,
    val signerChanged: Boolean,
) {
    val expandsAccess: Boolean
        get() = addedHosts.isNotEmpty() ||
            addedCapabilities.isNotEmpty() ||
            signerChanged

    companion object {
        fun between(
            old: PluginManifest,
            new: PluginManifest,
            signerChanged: Boolean,
        ): CapabilityDiff = CapabilityDiff(
            addedHosts = new.allowedHosts - old.allowedHosts,
            removedHosts = old.allowedHosts - new.allowedHosts,
            addedCapabilities = new.capabilities.map { it.name }.toSet() -
                old.capabilities.map { it.name }.toSet(),
            signerChanged = signerChanged,
        )
    }
}
