package app.openstory.settings

import app.openstory.chapters.notification.NotificationEventRepository
import app.openstory.notifications.NotificationPermissionGate
import app.openstory.notifications.PermissionRequestOutcome
import app.openstory.settings.notification.NotificationControlPort
import app.openstory.settings.notification.NotificationPermissionRequestResult
import app.openstory.settings.notification.SettingsNotificationStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class AndroidNotificationControlAdapter(
    private val gate: NotificationPermissionGate,
    events: NotificationEventRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : NotificationControlPort {
    private val platformStatus = flow {
        while (true) {
            emit(gate.status())
            delay(STATUS_REFRESH_MILLIS)
        }
    }

    override val status: Flow<SettingsNotificationStatus> = combine(
        platformStatus,
        events.observeRecentInAppOnlyCount(nowEpochMillis() - RECENT_WINDOW_MILLIS),
    ) { platform, recentCount ->
        SettingsNotificationStatus(platform.permissionGranted, platform.channelEnabled, recentCount)
    }

    override suspend fun requestPermission(): NotificationPermissionRequestResult = when (gate.requestPermission()) {
        PermissionRequestOutcome.GRANTED -> NotificationPermissionRequestResult.Granted
        PermissionRequestOutcome.DENIED -> NotificationPermissionRequestResult.Denied
        PermissionRequestOutcome.FAILED ->
            NotificationPermissionRequestResult.Failed("settings.notification_permission_failed")
    }

    private companion object {
        const val STATUS_REFRESH_MILLIS = 2_000L
        const val RECENT_WINDOW_MILLIS = 7 * 24 * 60 * 60 * 1000L
    }
}
