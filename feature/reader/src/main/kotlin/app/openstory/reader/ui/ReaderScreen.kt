package app.openstory.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.openstory.designsystem.glass.HikariBackdropHost
import app.openstory.designsystem.glass.HikariBackdropScope
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
    val progressState = remember(state.document?.fingerprint) {
        ReaderProgressUiState(fractionToPercent(state.restoredProgressFraction))
    }
    val closeReader = {
        actions.onFlushProgress()
        onBack()
    }

    HikariBackdropHost(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        background = {
            ReaderBackground(
                state = state,
                onToggleChrome = { chromeVisible = !chromeVisible },
                onPositionChanged = { position, reachedEnd ->
                    progressState.update(position.fraction)
                    actions.onPositionChanged(position, reachedEnd)
                },
                onRetry = actions.onRetry,
            )
        },
    ) {
        ReaderOverlay(
            state = state,
            actions = actions,
            progressState = progressState,
            chromeVisible = chromeVisible,
            settingsVisible = settingsVisible,
            backdropScope = this@HikariBackdropHost,
            onBack = closeReader,
            onOpenSettings = { settingsVisible = true },
            onDismissSettings = { settingsVisible = false },
        )
    }
}

@Composable
private fun ReaderOverlay(
    state: ReaderUiState,
    actions: ReaderActions,
    progressState: ReaderProgressUiState,
    chromeVisible: Boolean,
    settingsVisible: Boolean,
    backdropScope: HikariBackdropScope,
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
            ReaderProgressNavigation(
                state = state,
                actions = actions,
                progressState = progressState,
                backdropScope = backdropScope,
                modifier = Modifier.align(Alignment.BottomCenter).testTag("reader-bottom-chrome"),
            )
        }
        if (settingsVisible) ReaderSettingsSheet(state, actions, onDismissSettings)
    }
}

@Composable
private fun ReaderProgressNavigation(
    state: ReaderUiState,
    actions: ReaderActions,
    progressState: ReaderProgressUiState,
    backdropScope: HikariBackdropScope,
    modifier: Modifier,
) {
    ReaderChapterNavigation(
        state = state,
        progressPercent = progressState.percent,
        backdropScope = backdropScope,
        actions = actions,
        modifier = modifier,
    )
}

@Composable
private fun ReaderBackground(
    state: ReaderUiState,
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
                contentPadding = PaddingValues(
                    top = MaterialTheme.hikariDimensions.readerTopInset,
                    bottom = MaterialTheme.hikariDimensions.readerBottomInset,
                ),
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

@Stable
internal class ReaderProgressUiState(initialPercent: Int = 0) {
    var percent by mutableIntStateOf(initialPercent.coerceIn(0, PERCENT_MAX))
        private set

    fun update(fraction: Float) {
        val next = fractionToPercent(fraction)
        if (next != percent) percent = next
    }
}

internal fun fractionToPercent(fraction: Float): Int =
    (fraction.coerceIn(0f, 1f) * PERCENT_MAX).toInt()

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

private const val PERCENT_MAX = 100
