package app.openstory

import android.app.Application
import app.openstory.common.execution.BoundedProcessWorkAdmission
import app.openstory.common.execution.ProcessWorkAdmission
import app.openstory.execution.ProcessWorkAdmissionOwner
import app.openstory.startup.TRACE_APPLICATION_CREATED
import app.openstory.startup.startupTraceSection

class HikariApplication : Application(), ProcessWorkAdmissionOwner {
    override val processWorkAdmission: ProcessWorkAdmission by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BoundedProcessWorkAdmission()
    }

    override fun onCreate() {
        startupTraceSection(TRACE_APPLICATION_CREATED) {
            super.onCreate()
        }
    }
}
