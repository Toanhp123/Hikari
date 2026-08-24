package app.openstory.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.common.id.PluginId
import app.openstory.settings.background.BackgroundWorkStatusPort
import app.openstory.settings.background.SettingsBackgroundWorkStatus
import app.openstory.settings.notification.NotificationControlPort
import app.openstory.settings.notification.NotificationPermissionRequestResult
import app.openstory.settings.notification.SettingsNotificationStatus
import app.openstory.settings.session.PluginLoginCommandResult
import app.openstory.settings.session.PluginSessionControlPort
import app.openstory.settings.session.SettingsPluginSessionSummary
import app.openstory.settings.storage.SettingsStorageSummary
import app.openstory.settings.storage.StorageSummaryPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val pluginSessions: List<SettingsPluginSessionSummary> = emptyList(),
    val notificationStatus: SettingsNotificationStatus? = null,
    val backgroundWorkStatus: SettingsBackgroundWorkStatus? = null,
    val storageSummary: SettingsStorageSummary? = null,
    val authenticationSubmitting: Boolean = false,
    val notificationPermissionSubmitting: Boolean = false,
    val authenticationErrorCode: String? = null,
    val notificationErrorCode: String? = null,
    val statusErrorCode: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pluginSessions: PluginSessionControlPort,
    private val notifications: NotificationControlPort,
    private val backgroundWork: BackgroundWorkStatusPort,
    private val storage: StorageSummaryPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        collectSessions()
        collectNotificationStatus()
        collectBackgroundStatus()
        collectStorageStatus()
    }

    fun login(pluginId: String) {
        val id = runCatching { PluginId(pluginId) }.getOrNull()
        if (id == null) {
            update { copy(authenticationErrorCode = "settings.auth_plugin_id_invalid") }
            return
        }
        viewModelScope.launch {
            update { copy(authenticationSubmitting = true, authenticationErrorCode = null) }
            try {
                when (val result = pluginSessions.beginLogin(id)) {
                    PluginLoginCommandResult.Launched -> Unit
                    is PluginLoginCommandResult.Rejected ->
                        update { copy(authenticationErrorCode = result.errorCode) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                update { copy(authenticationErrorCode = "settings.auth_login_failed") }
            } finally {
                update { copy(authenticationSubmitting = false) }
            }
        }
    }

    fun logout(pluginId: String) {
        val id = runCatching { PluginId(pluginId) }.getOrNull()
        if (id == null) {
            update { copy(authenticationErrorCode = "settings.auth_plugin_id_invalid") }
            return
        }
        viewModelScope.launch {
            update { copy(authenticationSubmitting = true, authenticationErrorCode = null) }
            try {
                pluginSessions.logout(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                update { copy(authenticationErrorCode = "settings.auth_logout_failed") }
            } finally {
                update { copy(authenticationSubmitting = false) }
            }
        }
    }

    fun requestNotificationPermission() {
        viewModelScope.launch {
            update { copy(notificationPermissionSubmitting = true, notificationErrorCode = null) }
            try {
                when (val result = notifications.requestPermission()) {
                    NotificationPermissionRequestResult.Granted -> Unit
                    NotificationPermissionRequestResult.Denied ->
                        update { copy(notificationErrorCode = "settings.notification_permission_denied") }
                    is NotificationPermissionRequestResult.Failed ->
                        update { copy(notificationErrorCode = result.errorCode) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                update { copy(notificationErrorCode = "settings.notification_permission_failed") }
            } finally {
                update { copy(notificationPermissionSubmitting = false) }
            }
        }
    }

    fun clearErrors() = update {
        copy(authenticationErrorCode = null, notificationErrorCode = null, statusErrorCode = null)
    }

    private fun collectSessions() = collectStatus("settings.session_status_unavailable") {
        pluginSessions.sessions.collect { sessions -> update { copy(pluginSessions = sessions) } }
    }

    private fun collectNotificationStatus() = collectStatus("settings.notification_status_unavailable") {
        notifications.status.collect { status -> update { copy(notificationStatus = status) } }
    }

    private fun collectBackgroundStatus() = collectStatus("settings.background_status_unavailable") {
        backgroundWork.observe().collect { status -> update { copy(backgroundWorkStatus = status) } }
    }

    private fun collectStorageStatus() = collectStatus("settings.storage_status_unavailable") {
        storage.observe().collect { summary -> update { copy(storageSummary = summary) } }
    }

    private fun collectStatus(errorCode: String, collect: suspend () -> Unit) = viewModelScope.launch {
        try {
            collect()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            update { copy(statusErrorCode = errorCode) }
        }
    }

    private inline fun update(transform: SettingsUiState.() -> SettingsUiState) {
        mutableState.value = mutableState.value.transform()
    }
}
