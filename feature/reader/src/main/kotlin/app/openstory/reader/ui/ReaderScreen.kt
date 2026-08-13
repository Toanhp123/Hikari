package app.openstory.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.openstory.designsystem.glass.HikariBackdropHost
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    FlushProgressOnStop(actions.onFlushProgress)
    var chromeVisible by remember { mutableStateOf(true) }
    var settingsVisible by remember { mutableStateOf(false) }
    var progress by remember(state.document?.fingerprint) { mutableFloatStateOf(0f) }
    val closeReader = {
        actions.onFlushProgress()
        onBack()
    }

    HikariBackdropHost(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        background = {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                when {
                    state.loading -> Centered { HikariLoadingState(label = "Loading reader") }
                    state.document != null -> ReaderContent(
                        document = state.document,
                        fontScale = state.fontScale,
                        restoredBlockId = state.restoredBlockId,
                        restoredCharacterOffset = state.restoredCharacterOffset,
                        contentPadding = if (chromeVisible) {
                            PaddingValues(top = 104.dp, bottom = 96.dp)
                        } else {
                            PaddingValues.Zero
                        },
                        onPositionChanged = { position, reachedEnd ->
                            progress = position.fraction
                            actions.onPositionChanged(position, reachedEnd)
                        },
                        modifier = Modifier.testTag("reader-content"),
                        onToggleChrome = { chromeVisible = !chromeVisible },
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
        },
    ) {
        val backdropScope = this@HikariBackdropHost
        Box(Modifier.fillMaxSize()) {
            if (chromeVisible && !settingsVisible && !state.loading && state.document != null) {
                ReaderControls(
                    state = state,
                    backdropScope = backdropScope,
                    onBack = closeReader,
                    onSettings = { settingsVisible = true },
                    modifier = Modifier.align(Alignment.TopCenter).testTag("reader-top-chrome"),
                )
                ReaderChapterNavigation(
                    state = state,
                    progress = progress,
                    backdropScope = backdropScope,
                    actions = actions,
                    modifier = Modifier.align(Alignment.BottomCenter).testTag("reader-bottom-chrome"),
                )
            }
            if (settingsVisible) {
                ReaderSettingsSheet(
                    state = state,
                    actions = actions,
                    onDismiss = { settingsVisible = false },
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
