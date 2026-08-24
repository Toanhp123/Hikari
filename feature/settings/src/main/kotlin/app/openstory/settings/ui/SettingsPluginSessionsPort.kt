package app.openstory.settings.ui

import kotlinx.coroutines.flow.Flow

enum class SettingsPluginSessionStatus { LOGGED_OUT, AUTHENTICATED, EXPIRED }

data class SettingsPluginSessionSummary(
    val pluginId: String,
    val displayName: String,
    val status: SettingsPluginSessionStatus,
    val expiresAtEpochMillis: Long?,
)

interface SettingsPluginSessionsPort {
    fun observeInstalledSessions(): Flow<List<SettingsPluginSessionSummary>>
    suspend fun launchLogin(pluginId: String): Boolean
    suspend fun logout(pluginId: String)
}
