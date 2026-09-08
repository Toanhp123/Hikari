package app.openstory

import android.app.Application
import app.openstory.startup.TRACE_APPLICATION_CREATED
import app.openstory.startup.startupTraceSection

class HikariApplication : Application() {
    override fun onCreate() {
        startupTraceSection(TRACE_APPLICATION_CREATED) {
            super.onCreate()
        }
    }
}
