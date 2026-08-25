package app.openstory.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.control.HikariPrimaryAction
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariFocusedHeader
import app.openstory.designsystem.layout.HikariStickyDestinationScaffold
import app.openstory.designsystem.layout.withScreenContentInsets
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.settings.session.SettingsPluginSessionStatus
import app.openstory.settings.session.SettingsPluginSessionSummary

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onLogin: (String) -> Unit,
    onLogout: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    HikariDestinationScaffold(modifier) {
        HikariStickyDestinationScaffold(
            contentPadding = contentPadding,
            header = { HikariFocusedHeader("Settings", onBack) },
        ) { bodyPadding ->
            LazyColumn(
                modifier = Modifier.testTag("settings-list"),
                contentPadding = bodyPadding.withScreenContentInsets(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
            ) {
                item("notification-status") {
                    SettingsStatusCard(
                        title = "Notifications",
                        lines = listOfNotNull(
                            state.notificationStatus?.let {
                                if (it.permissionGranted) "Permission granted" else "Permission required"
                            },
                            state.notificationStatus?.let {
                                if (it.channelEnabled) "Chapter channel enabled" else "Chapter channel disabled"
                            },
                            state.notificationStatus?.recentInAppOnlyCount?.let {
                                "$it recent updates kept in app"
                            },
                        ),
                        contentDescription = "Notification status",
                    ) {
                        if (state.notificationStatus?.permissionGranted == false) {
                            HikariPrimaryAction(
                                onClick = onRequestNotificationPermission,
                                enabled = !state.notificationPermissionSubmitting,
                            ) { Text("Allow notifications") }
                        }
                    }
                }
                item("background-status") {
                    val background = state.backgroundWorkStatus
                    SettingsStatusCard(
                        title = "Background chapter checks",
                        lines = listOfNotNull(
                            background?.let { if (it.registered) "Scheduled" else "Not scheduled" },
                            background?.lastDispatchAtEpochMillis?.let { "Last dispatch: $it" },
                            background?.lastErrorCode?.let(::stableErrorCopy),
                        ),
                        contentDescription = "Background work status",
                    )
                }
                item("storage-status") {
                    val storage = state.storageSummary
                    SettingsStatusCard(
                        title = "Storage",
                        lines = listOfNotNull(
                            storage?.let { "Total: ${it.totalBytes.byteLabel()}" },
                            storage?.let {
                                "Automatic cache: ${it.automaticCacheBytes.byteLabel()} / " +
                                    it.automaticCacheQuotaBytes.byteLabel()
                            },
                        ),
                        contentDescription = "Storage summary",
                    )
                }
                items(state.pluginSessions, key = { it.pluginId.value }) { session ->
                    PluginSessionCard(session, state.authenticationSubmitting, onLogin, onLogout)
                }
                listOfNotNull(
                    state.authenticationErrorCode,
                    state.notificationErrorCode,
                    state.statusErrorCode,
                ).distinct().forEach { errorCode ->
                    item("error-$errorCode") {
                        HikariInlineFeedback(message = stableErrorCopy(errorCode))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsStatusCard(
    title: String,
    lines: List<String>,
    contentDescription: String,
    action: @Composable () -> Unit = {},
) {
    HikariContentCard(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        },
    ) {
        Column(
            Modifier.padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            action()
        }
    }
}

@Composable
private fun PluginSessionCard(
    session: SettingsPluginSessionSummary,
    submitting: Boolean,
    onLogin: (String) -> Unit,
    onLogout: (String) -> Unit,
) {
    SettingsStatusCard(
        title = session.displayName,
        lines = listOf(
            when (session.status) {
                SettingsPluginSessionStatus.LOGGED_OUT -> "Logged out"
                SettingsPluginSessionStatus.AUTHENTICATED -> "Authenticated"
                SettingsPluginSessionStatus.EXPIRED -> "Session expired"
            },
        ),
        contentDescription = "Plugin session ${session.displayName}",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
            if (session.status == SettingsPluginSessionStatus.AUTHENTICATED) {
                HikariUtilityAction(
                    onClick = { onLogout(session.pluginId.value) },
                    enabled = !submitting,
                ) { Text("Log out") }
            } else {
                HikariPrimaryAction(
                    onClick = { onLogin(session.pluginId.value) },
                    enabled = !submitting,
                ) { Text("Log in") }
            }
        }
    }
}

private fun stableErrorCopy(errorCode: String): String = when (errorCode) {
    "settings.auth_login_unavailable" -> "Login is unavailable for this plugin."
    "settings.auth_login_failed" -> "Login could not be started."
    "settings.auth_logout_failed" -> "Logout could not be completed."
    "settings.notification_permission_denied" -> "Notification permission was not granted."
    "settings.notification_permission_failed" -> "Notification permission could not be requested."
    "settings.background_status_unavailable" -> "Background status is temporarily unavailable."
    "settings.storage_status_unavailable" -> "Storage status is temporarily unavailable."
    else -> "This status is temporarily unavailable."
}

private fun Long.byteLabel(): String = when {
    this >= BYTES_PER_GIGABYTE -> "${this / BYTES_PER_GIGABYTE} GB"
    this >= BYTES_PER_MEGABYTE -> "${this / BYTES_PER_MEGABYTE} MB"
    this >= BYTES_PER_KILOBYTE -> "${this / BYTES_PER_KILOBYTE} KB"
    else -> "$this B"
}

private const val BYTES_PER_KILOBYTE = 1L shl 10
private const val BYTES_PER_MEGABYTE = 1L shl 20
private const val BYTES_PER_GIGABYTE = 1L shl 30
