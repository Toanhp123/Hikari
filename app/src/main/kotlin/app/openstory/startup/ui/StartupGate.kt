package app.openstory.startup.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import app.openstory.startup.AppLaunchState
import app.openstory.startup.AppLaunchStateStore
import app.openstory.startup.TRACE_FIRST_FRAME
import app.openstory.startup.TRACE_LAUNCH_STATE_RESOLVED
import app.openstory.startup.createAppLaunchStateStore
import app.openstory.startup.startupTraceMark
import app.openstory.ui.HikariBootSurface
import app.openstory.ui.HikariBootTheme
import kotlinx.coroutines.launch

@Composable
internal fun HikariStartupApp() {
    val context = LocalContext.current.applicationContext
    val store = remember(context) {
        createAppLaunchStateStore(context)
    }

    LaunchedEffect(Unit) {
        withFrameNanos {
            startupTraceMark(TRACE_FIRST_FRAME)
        }
    }

    HikariBootTheme {
        HikariBootSurface {
            StartupGate(store)
        }
    }
}

@Composable
internal fun StartupGate(store: AppLaunchStateStore) {
    var launchState by remember { mutableStateOf<AppLaunchState>(AppLaunchState.Unknown) }
    var saveInFlight by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(store) {
        launchState = store.resolve()
        startupTraceMark(TRACE_LAUNCH_STATE_RESOLVED)
    }

    fun completeInitialSetup() {
        if (saveInFlight) return
        saveInFlight = true
        saveFailed = false
        scope.launch {
            val persisted = store.markInitialSetupCompleted()
            saveInFlight = false
            if (persisted) {
                launchState = AppLaunchState.Ready
            } else {
                saveFailed = true
            }
        }
    }

    when (launchState) {
        AppLaunchState.Unknown -> UnknownScreen()
        AppLaunchState.FirstRun -> FirstRunScreen(
            isSaving = saveInFlight,
            saveFailed = saveFailed,
            onComplete = ::completeInitialSetup,
        )
        AppLaunchState.Ready -> HomeShell()
    }
}
