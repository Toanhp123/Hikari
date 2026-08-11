package app.openstory.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
) {
    FlushProgressOnStop(actions.onFlushProgress)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ReaderControls(state, actions) },
        bottomBar = { ReaderChapterNavigation(state, actions) },
    ) { padding ->
        when {
            state.loading -> Centered {
                HikariLoadingState(label = "Loading reader")
            }
            state.document != null -> ReaderContent(
                document = state.document,
                fontScale = state.fontScale,
                restoredBlockId = state.restoredBlockId,
                restoredCharacterOffset = state.restoredCharacterOffset,
                contentPadding = padding,
                onPositionChanged = actions.onPositionChanged,
            )
            else -> Centered {
                HikariErrorState(
                    title = "Reader unavailable",
                    message = state.failure,
                    actionLabel = "Retry",
                    onAction = actions.onRetry,
                )
            }
        }
    }
}

@Composable
private fun FlushProgressOnStop(onFlush: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, onFlush) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onFlush()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            onFlush()
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
