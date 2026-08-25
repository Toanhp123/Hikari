package app.openstory.settings

import android.content.Context
import android.content.Intent
import app.openstory.auth.PluginLoginActivity
import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.auth.InstalledAuthenticationPolicySource
import app.openstory.plugins.runtime.auth.PluginSessionService
import app.openstory.plugins.runtime.auth.PluginSessionStatus
import app.openstory.settings.session.PluginLoginCommandResult
import app.openstory.settings.session.PluginSessionControlPort
import app.openstory.settings.session.SettingsPluginSessionStatus
import app.openstory.settings.session.SettingsPluginSessionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RuntimePluginSessionControlAdapter(
    context: Context,
    private val sessionService: PluginSessionService,
    private val policies: InstalledAuthenticationPolicySource,
) : PluginSessionControlPort {
    private val applicationContext = context.applicationContext

    override val sessions: Flow<List<SettingsPluginSessionSummary>> =
        sessionService.observeInstalledSessions().map { summaries ->
            policies.installedAuthenticationPolicies().filter { it.enabled }.map { policy ->
                val summary = summaries.singleOrNull { it.pluginId == policy.pluginId }
                SettingsPluginSessionSummary(
                    pluginId = policy.pluginId,
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

    override suspend fun beginLogin(pluginId: PluginId): PluginLoginCommandResult = try {
        val installed = policies.installedAuthenticationPolicies().any { it.pluginId == pluginId && it.enabled }
        if (!installed) return PluginLoginCommandResult.Rejected("settings.auth_login_unavailable")
        applicationContext.startActivity(
            Intent(applicationContext, PluginLoginActivity::class.java)
                .putExtra(PluginLoginActivity.EXTRA_PLUGIN_ID, pluginId.value)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        PluginLoginCommandResult.Launched
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        PluginLoginCommandResult.Rejected("settings.auth_login_failed")
    }

    override suspend fun logout(pluginId: PluginId) = sessionService.logout(pluginId)
}
