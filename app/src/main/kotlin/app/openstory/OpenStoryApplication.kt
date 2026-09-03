package app.openstory

import android.app.Application
import app.openstory.cache.AutomaticCachePolicyCoordinator
import app.openstory.notifications.NotificationChannelConfig
import app.openstory.notifications.WorkManagerNotificationDrainScheduler
import app.openstory.reader.assets.ReaderAssetImageLoaderInstaller
import app.openstory.settings.background.BackgroundPolicyCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class OpenStoryApplication : Application() {
    @Inject lateinit var automaticCachePolicyCoordinator: AutomaticCachePolicyCoordinator
    @Inject lateinit var backgroundPolicyCoordinator: BackgroundPolicyCoordinator
    @Inject lateinit var notificationDrainScheduler: WorkManagerNotificationDrainScheduler
    @Inject lateinit var readerAssetImageLoaderInstaller: ReaderAssetImageLoaderInstaller
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        readerAssetImageLoaderInstaller.install()
        NotificationChannelConfig.create(this)
        automaticCachePolicyCoordinator.start(applicationScope)
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
