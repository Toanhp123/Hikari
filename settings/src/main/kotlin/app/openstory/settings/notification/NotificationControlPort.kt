package app.openstory.settings.notification

import kotlinx.coroutines.flow.Flow

interface NotificationControlPort {
    val status: Flow<SettingsNotificationStatus>
    suspend fun requestPermission(): NotificationPermissionRequestResult
}

data class SettingsNotificationStatus(
    val permissionGranted: Boolean,
    val channelEnabled: Boolean,
    val recentInAppOnlyCount: Int,
)

sealed interface NotificationPermissionRequestResult {
    data object Granted : NotificationPermissionRequestResult
    data object Denied : NotificationPermissionRequestResult
    data class Failed(val errorCode: String) : NotificationPermissionRequestResult
}
