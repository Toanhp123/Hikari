package app.openstory.plugins.runtime.capabilities.log

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.persistence.PluginDiagnosticEvent
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import kotlinx.serialization.Serializable

@Serializable
data class SafeLogEvent(val code: String, val detail: String? = null)

class SafePluginLogger(
    private val diagnostics: PluginDiagnosticsSink,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun log(
        pluginId: PluginId,
        operation: String?,
        event: SafeLogEvent,
    ): PluginCallResult<Unit> = try {
        diagnostics.record(
            PluginDiagnosticEvent(pluginId, event.code, operation, nowEpochMillis(), event.detail),
        )
        PluginCallResult.Success(Unit)
    } catch (_: IllegalArgumentException) {
        PluginCallResult.Failure("plugin.log_event_invalid", retryable = false)
    }
}
