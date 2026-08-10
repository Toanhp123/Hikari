package app.openstory.plugins.runtime.persistence

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.SAFE_CODE
import app.openstory.plugins.runtime.requireSafeDetail

data class PluginDiagnosticEvent(
    val pluginId: PluginId,
    val code: String,
    val operation: String?,
    val occurredAtEpochMillis: Long,
    val safeDetail: String? = null,
) {
    init {
        require(SAFE_CODE.matches(code)) { "Diagnostic code must be safe" }
        require(operation == null || SAFE_OPERATION.matches(operation)) { "Diagnostic operation must be safe" }
        require(occurredAtEpochMillis >= 0L) { "Diagnostic timestamp must not be negative" }
        requireSafeDetail(safeDetail)
    }

    private companion object {
        val SAFE_OPERATION = Regex("[a-z]+(?:[.][a-z]+)+")
    }
}

interface PluginDiagnosticsSink {
    suspend fun record(event: PluginDiagnosticEvent)
    suspend fun recent(pluginId: PluginId, limit: Int): List<PluginDiagnosticEvent>
}
