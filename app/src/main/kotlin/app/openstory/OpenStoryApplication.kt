package app.openstory

import android.app.Application
import app.openstory.plugin.host.PluginHost
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OpenStoryApplication : Application() {
    @Inject
    internal lateinit var pluginHost: PluginHost
}
