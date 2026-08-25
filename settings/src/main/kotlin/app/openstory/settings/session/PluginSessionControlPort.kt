package app.openstory.settings.session

import app.openstory.common.id.PluginId
import kotlinx.coroutines.flow.Flow

interface PluginSessionControlPort {
    val sessions: Flow<List<SettingsPluginSessionSummary>>
    suspend fun beginLogin(pluginId: PluginId): PluginLoginCommandResult
    suspend fun logout(pluginId: PluginId)
}

data class SettingsPluginSessionSummary(
    val pluginId: PluginId,
    val displayName: String,
    val status: SettingsPluginSessionStatus,
    val expiresAtEpochMillis: Long?,
)

enum class SettingsPluginSessionStatus { LOGGED_OUT, AUTHENTICATED, EXPIRED }

sealed interface PluginLoginCommandResult {
    data object Launched : PluginLoginCommandResult
    data class Rejected(val errorCode: String) : PluginLoginCommandResult
}
