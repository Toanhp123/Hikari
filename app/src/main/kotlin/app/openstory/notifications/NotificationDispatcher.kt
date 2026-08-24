package app.openstory.notifications

import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.CancellationException

sealed interface PlatformNotificationResult {
    data object Published : PlatformNotificationResult
    data class InAppOnly(val reasonCode: String) : PlatformNotificationResult
    data class RetryableFailure(val errorCode: String) : PlatformNotificationResult
}

fun interface AndroidChapterNotifier {
    suspend fun publish(
        notificationId: Int,
        target: ValidatedChapterNotificationTarget,
    ): PlatformNotificationResult
}

class NotificationDispatcher(
    context: Context,
    private val permissionGate: NotificationPermissionGate,
    private val builder: StoryNotificationBuilder,
) : AndroidChapterNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override suspend fun publish(
        notificationId: Int,
        target: ValidatedChapterNotificationTarget,
    ): PlatformNotificationResult {
        val status = permissionGate.status()
        return when {
            !status.permissionGranted ->
                PlatformNotificationResult.InAppOnly("notification.permission_denied")
            !status.channelEnabled ->
                PlatformNotificationResult.InAppOnly("notification.channel_disabled")
            else -> publishToPlatform(notificationId, target)
        }
    }

    private fun publishToPlatform(
        notificationId: Int,
        target: ValidatedChapterNotificationTarget,
    ): PlatformNotificationResult = try {
            manager.notify(notificationId, builder.build(target))
            PlatformNotificationResult.Published
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            PlatformNotificationResult.InAppOnly("notification.permission_denied")
        } catch (_: RuntimeException) {
            PlatformNotificationResult.RetryableFailure("notification.platform_publish_failed")
        }
}
