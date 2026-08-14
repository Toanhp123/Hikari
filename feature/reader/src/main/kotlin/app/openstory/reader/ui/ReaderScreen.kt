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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.openstory.designsystem.glass.HikariBackdropHost
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.reader.progress.ReadingPosition

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
            ReaderBackground(
                state = state,
                chromeVisible = chromeVisible,
                onToggleChrome = { chromeVisible = !chromeVisible },
                onPositionChanged = { position, reachedEnd ->
                    progress = position.fraction
                    actions.onPositionChanged(position, reachedEnd)
                },
                onRetry = actions.onRetry,
            )
        },
    ) {
        val backdropScope = this@HikariBackdropHost
        ReaderOverlay(
            state,
            actions,
            progress,
            chromeVisible,
            settingsVisible,
            backdropScope,
            closeReader,
            { settingsVisible = true },
            { settingsVisible = false },
        )
    }
}

@Composable
private fun ReaderOverlay(
    state: ReaderUiState,
    actions: ReaderActions,
    progress: Float,
    chromeVisible: Boolean,
    settingsVisible: Boolean,
    backdropScope: app.openstory.designsystem.glass.HikariBackdropScope,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        val canShowControls = !settingsVisible && !state.loading && state.document != null
        if (chromeVisible && canShowControls) {
            ReaderControls(
                state,
                backdropScope,
                onBack,
                onOpenSettings,
                Modifier.align(Alignment.TopCenter).testTag("reader-top-chrome"),
            )
            ReaderChapterNavigation(
                state,
                progress,
                backdropScope,
                actions,
                Modifier.align(Alignment.BottomCenter).testTag("reader-bottom-chrome"),
            )
        }
        if (settingsVisible) ReaderSettingsSheet(state, actions, onDismissSettings)
    }
}

@Composable
private fun ReaderBackground(
    state: ReaderUiState,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onPositionChanged: (ReadingPosition, Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        when {
            state.loading -> Centered { HikariLoadingState(label = "Loading reader") }
            state.document != null -> ReaderContent(
                document = state.document,
                fontScale = state.fontScale,
                restoredBlockId = state.restoredBlockId,
                restoredCharacterOffset = state.restoredCharacterOffset,
                contentPadding = if (chromeVisible) {
                    PaddingValues(
                        top = MaterialTheme.hikariDimensions.readerTopInset,
                        bottom = MaterialTheme.hikariDimensions.readerBottomInset,
                    )
                } else {
                    PaddingValues.Zero
                },
                onPositionChanged = onPositionChanged,
                modifier = Modifier.testTag("reader-content"),
                onToggleChrome = onToggleChrome,
            )
            else -> Centered {
                HikariErrorState(
                    title = "Reader unavailable",
                    message = state.failure,
                    actionLabel = "Retry",
                    onAction = onRetry,
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
