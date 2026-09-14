package app.openstory

import android.app.Application
import app.openstory.common.execution.BoundedProcessWorkAdmission
import app.openstory.startup.TRACE_APPLICATION_CREATED
import app.openstory.startup.startupTraceSection

class HikariApplication : Application() {
    val processWorkAdmission by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BoundedProcessWorkAdmission()
    }

    override fun onCreate() {
        startupTraceSection(TRACE_APPLICATION_CREATED) {
            super.onCreate()
        }
    }
}
