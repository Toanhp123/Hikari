package app.openstory.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class NotificationPlatformStatus(
    val permissionGranted: Boolean,
    val channelEnabled: Boolean,
)

enum class PermissionRequestOutcome { GRANTED, DENIED, FAILED }

class NotificationPermissionGate(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    fun status(): NotificationPlatformStatus = NotificationPlatformStatus(
        permissionGranted = isPermissionGranted(),
        channelEnabled = NotificationChannelConfig.isEnabled(applicationContext),
    )

    fun isPermissionGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    suspend fun requestPermission(): PermissionRequestOutcome {
        if (isPermissionGranted()) return PermissionRequestOutcome.GRANTED
        return suspendCancellableCoroutine { continuation ->
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (!continuation.isActive) return
                    continuation.resume(
                        when (resultCode) {
                            Activity.RESULT_OK -> PermissionRequestOutcome.GRANTED
                            Activity.RESULT_CANCELED -> PermissionRequestOutcome.DENIED
                            else -> PermissionRequestOutcome.FAILED
                        },
                    )
                }
            }
            val intent = Intent(applicationContext, NotificationPermissionActivity::class.java)
                .putExtra(NotificationPermissionActivity.EXTRA_RESULT_RECEIVER, receiver)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                applicationContext.startActivity(intent)
            } catch (_: RuntimeException) {
                continuation.resume(PermissionRequestOutcome.FAILED)
            }
        }
    }
}
