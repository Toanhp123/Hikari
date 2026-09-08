package app.openstory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.openstory.startup.TRACE_ACTIVITY_CREATED
import app.openstory.startup.TRACE_CONTENT_REQUESTED
import app.openstory.startup.startupTraceMark
import app.openstory.startup.startupTraceSection
import app.openstory.startup.ui.HikariStartupApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        startupTraceSection(TRACE_ACTIVITY_CREATED) {
            super.onCreate(savedInstanceState)
        }
        enableEdgeToEdge()
        startupTraceMark(TRACE_CONTENT_REQUESTED)
        setContent {
            HikariStartupApp()
        }
    }
}
