package app.openstory.notifications

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver

class NotificationPermissionActivity : Activity() {
    private val receiver: ResultReceiver? by lazy {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            finishWith(RESULT_OK)
        } else if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            finishWith(RESULT_OK)
        } else {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            finishWith(
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) RESULT_OK else RESULT_CANCELED,
            )
        }
    }

    private fun finishWith(result: Int) {
        receiver?.send(result, Bundle.EMPTY)
        finish()
    }

    companion object {
        const val EXTRA_RESULT_RECEIVER = "app.openstory.extra.NOTIFICATION_PERMISSION_RECEIVER"
        private const val REQUEST_CODE = 7001
    }
}
