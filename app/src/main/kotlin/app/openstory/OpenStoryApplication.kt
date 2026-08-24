package app.openstory

import android.app.Application
import app.openstory.settings.background.BackgroundPolicyCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OpenStoryApplication : Application() {
    @Inject lateinit var backgroundPolicyCoordinator: BackgroundPolicyCoordinator

    override fun onCreate() {
        super.onCreate()
        backgroundPolicyCoordinator.start()
    }
}
