package app.openstory.plugins.runtime.update

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.persistence.PluginStateStore

class PluginRollbackService(
    private val state: PluginStateStore,
    private val onRolledBack: suspend (PluginId) -> Unit = {},
) {
    suspend fun rollback(pluginId: PluginId): PluginCallResult<Unit> {
        val current = state.find(pluginId)
        return when {
            current == null -> PluginCallResult.Failure("plugin.not_installed", false)
            current.previousVersion == null -> PluginCallResult.Failure("plugin.rollback_unavailable", false)
            else -> {
                state.replace(
                    current.copy(
                        activeVersion = current.previousVersion,
                        previousVersion = current.activeVersion,
                    ),
                )
                onRolledBack(pluginId)
                PluginCallResult.Success(Unit)
            }
        }
    }
}
