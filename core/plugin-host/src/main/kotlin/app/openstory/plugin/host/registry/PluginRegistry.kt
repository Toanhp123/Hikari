package app.openstory.plugin.host.registry

import app.openstory.common.AppResult
import app.openstory.plugin.host.install.InstalledPlugin
import app.openstory.plugin.host.install.StagedPluginPackage

data class PluginRegistration(
    val pluginId: String,
    val enabled: Boolean,
    val activeVersion: String?,
    val previousVersion: String?,
)

interface PluginRegistry {
    suspend fun find(
        pluginId: String,
    ): PluginRegistration?
}

interface MutablePluginRegistry : PluginRegistry {
    suspend fun activate(
        stagedPackage: StagedPluginPackage,
    ): AppResult<InstalledPlugin>

    suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): AppResult<Unit>
}
