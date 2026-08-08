package app.openstory.plugin.host.update

import app.openstory.common.AppResult
import app.openstory.plugin.host.install.InstalledPlugin
import app.openstory.plugin.host.install.InstalledPluginPackageLookup
import app.openstory.plugin.host.install.PluginRollbackManager
import app.openstory.plugin.host.registry.MutablePluginRegistry

class PluginRollbackService(
    packageLookup: InstalledPluginPackageLookup,
    registry: MutablePluginRegistry,
) {
    private val manager = PluginRollbackManager(packageLookup, registry)

    suspend fun rollback(pluginId: String): AppResult<InstalledPlugin> =
        manager.rollback(pluginId)
}
