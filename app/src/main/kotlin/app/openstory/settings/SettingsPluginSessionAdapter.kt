package app.openstory.settings

import android.content.Context
import android.content.Intent
import app.openstory.auth.PluginLoginActivity
import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.auth.InstalledAuthenticationPolicySource
import app.openstory.plugins.runtime.auth.PluginSessionService
import app.openstory.plugins.runtime.auth.PluginSessionStatus
import app.openstory.settings.ui.SettingsPluginSessionStatus
import app.openstory.settings.ui.SettingsPluginSessionSummary
import app.openstory.settings.ui.SettingsPluginSessionsPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsPluginSessionAdapter(
    private val context: Context,
    private val sessions: PluginSessionService,
    private val policies: InstalledAuthenticationPolicySource,
) : SettingsPluginSessionsPort {
    override fun observeInstalledSessions(): Flow<List<SettingsPluginSessionSummary>> =
        sessions.observeInstalledSessions().map { summaries ->
            val installed = policies.installedAuthenticationPolicies()
            installed.filter { it.enabled }.map { policy ->
                val summary = summaries.singleOrNull { it.pluginId == policy.pluginId }
                SettingsPluginSessionSummary(
                    pluginId = policy.pluginId.value,
                    displayName = policy.pluginId.value,
                    status = when (summary?.status ?: PluginSessionStatus.LOGGED_OUT) {
                        PluginSessionStatus.LOGGED_OUT -> SettingsPluginSessionStatus.LOGGED_OUT
                        PluginSessionStatus.AUTHENTICATED -> SettingsPluginSessionStatus.AUTHENTICATED
                        PluginSessionStatus.EXPIRED -> SettingsPluginSessionStatus.EXPIRED
                    },
                    expiresAtEpochMillis = summary?.expiresAtEpochMillis,
                )
            }
        }

    override suspend fun launchLogin(pluginId: String): Boolean = try {
        val id = PluginId(pluginId)
        val available = policies.installedAuthenticationPolicies().any { it.pluginId == id && it.enabled }
        if (!available) return false
        context.startActivity(
            Intent(context, PluginLoginActivity::class.java)
                .putExtra(PluginLoginActivity.EXTRA_PLUGIN_ID, pluginId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    override suspend fun logout(pluginId: String) = sessions.logout(PluginId(pluginId))
}
