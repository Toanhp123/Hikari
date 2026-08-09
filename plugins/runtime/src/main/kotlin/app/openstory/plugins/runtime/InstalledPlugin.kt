package app.openstory.plugins.runtime

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService

data class InstalledPlugin(
    val pluginId: PluginId,
    val version: String,
    val services: Set<PluginService>,
) {
    init {
        require(version.isNotBlank()) { "Installed version must not be blank" }
        require(services.isNotEmpty()) { "Installed plugin must provide a service" }
    }
}
