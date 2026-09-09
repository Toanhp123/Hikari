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
import app.openstory.catalog.feature.CatalogEntryPoint
import app.openstory.startup.AppLaunchState
import app.openstory.startup.AppLaunchStateStore
import app.openstory.startup.TRACE_DESTINATION_READY
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
    var firstFrameReached by remember { mutableStateOf(false) }
    val store = remember(context) {
        createAppLaunchStateStore(context)
    }

    LaunchedEffect(Unit) {
        withFrameNanos {
            startupTraceMark(TRACE_FIRST_FRAME)
            firstFrameReached = true
        }
    }

    HikariBootTheme {
        HikariBootSurface {
            StartupGate(
                store = store,
                firstFrameReached = firstFrameReached,
            )
        }
    }
}

@Composable
internal fun StartupGate(
    store: AppLaunchStateStore,
    firstFrameReached: Boolean,
) {
    var launchState by remember { mutableStateOf<AppLaunchState>(AppLaunchState.Unknown) }
    var saveInFlight by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(store) {
        launchState = store.resolve()
        startupTraceMark(TRACE_LAUNCH_STATE_RESOLVED)
    }

    LaunchedEffect(launchState, firstFrameReached) {
        if (launchState == AppLaunchState.Ready && firstFrameReached) {
            startupTraceMark(TRACE_DESTINATION_READY)
        }
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
        AppLaunchState.Ready -> if (firstFrameReached) CatalogEntryPoint() else UnknownScreen()
    }
}
