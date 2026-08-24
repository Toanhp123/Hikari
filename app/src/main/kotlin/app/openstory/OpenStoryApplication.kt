package app.openstory

import android.app.Application
import app.openstory.notifications.NotificationChannelConfig
import app.openstory.notifications.WorkManagerNotificationDrainScheduler
import app.openstory.settings.background.BackgroundPolicyCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class OpenStoryApplication : Application() {
    @Inject lateinit var backgroundPolicyCoordinator: BackgroundPolicyCoordinator
    @Inject lateinit var notificationDrainScheduler: WorkManagerNotificationDrainScheduler
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationChannelConfig.create(this)
        backgroundPolicyCoordinator.start()
        applicationScope.launch {
            try {
                notificationDrainScheduler.ensureRecoveryWork()
            } catch (_: RuntimeException) {
                // Pending rows remain durable and the next foreground/event wake retries registration.
            }
        }
    }
}
