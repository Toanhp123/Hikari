package app.openstory.plugins.runtime

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.manifest.ReaderCapability

data class InstalledPlugin(
    val pluginId: PluginId,
    val version: String,
    val services: Set<PluginService>,
    val allowedNetworkHosts: Set<String> = emptySet(),
    val readerCapability: ReaderCapability? = null,
) {
    init {
        require(version.isNotBlank()) { "Installed version must not be blank" }
        require(services.isNotEmpty()) { "Installed plugin must provide a service" }
        require(allowedNetworkHosts.none(String::isBlank)) { "Allowed network hosts must not be blank" }
    }
}
