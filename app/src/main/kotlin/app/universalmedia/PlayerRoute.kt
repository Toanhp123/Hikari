package app.universalmedia

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.universalmedia.core.model.MediaId
import app.universalmedia.playback.api.PlaybackFailure
import app.universalmedia.playback.api.PlaybackPhase
import app.universalmedia.playback.api.PlaybackState
import app.universalmedia.playback.media3.InternalPlaybackController
import app.universalmedia.source.api.ResolutionFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

@Composable
internal fun PlayerRoute(
    mediaId: MediaId,
    coordinator: PlaybackCoordinator,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var result by remember(mediaId) { mutableStateOf<PlayerOpenResult?>(null) }
    var snapshot by remember(mediaId) { mutableStateOf<PlaybackState?>(null) }
    var retry by remember(mediaId) { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(mediaId, retry, lifecycle) {
        result = null
        snapshot = null
        // Screen cancellation must not interrupt a START already handed to the service.
        result = scope.async { coordinator.open(mediaId) }.await()
        if (result == PlayerOpenResult.Ready) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    snapshot = coordinator.controller?.state()
                    delay(250)
                }
            }
        }
    }
    val back = {
        coordinator.leave()
        onBack()
    }
    BackHandler(onBack = back)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Video")
        Button(onClick = back) { Text("Back to Library") }
        when (val opened = result) {
            null -> Text("Opening video...")

            is PlayerOpenResult.SourceFailed -> {
                Text(opened.failure.message())
                Button(onClick = { retry++ }) { Text("Retry") }
            }

            PlayerOpenResult.ProgressFailed -> {
                Text("Could not load saved progress.")
                Button(onClick = { retry++ }) { Text("Retry") }
            }

            PlayerOpenResult.ConnectionFailed -> {
                Text("Could not connect to playback.")
                Button(onClick = { retry++ }) { Text("Retry") }
            }

            PlayerOpenResult.Ready -> {
                val controller = coordinator.controller
                val adapter = controller as? InternalPlaybackController
                if (adapter != null) {
                    DisposableEffect(adapter) {
                        onDispose { adapter.attachVideoSurface(null) }
                    }
                    AndroidView(
                        factory = { context -> SurfaceView(context) },
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        update = { adapter.attachVideoSurface(it) },
                    )
                }
                val state = snapshot
                if (state?.phase == PlaybackPhase.FAILED) {
                    Text(state.failure.message())
                    Button(onClick = { retry++ }) { Text("Retry") }
                } else {
                    Text(
                        if (state?.phase ==
                            PlaybackPhase.BUFFERING
                        ) {
                            "Buffering..."
                        } else {
                            "Playback ready"
                        },
                    )
                    Text("Position: ${(state?.positionMs ?: 0) / 1000}s")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (controller?.state()?.isPlaying ==
                                true
                            ) {
                                controller.pause()
                            } else {
                                controller?.play()
                            }
                        }) { Text(if (state?.isPlaying == true) "Pause" else "Play") }
                        Button(onClick = {
                            controller?.state()?.let {
                                controller.seekTo((it.positionMs - 10_000).coerceAtLeast(0))
                            }
                        }) { Text("-10s") }
                        Button(onClick = {
                            controller?.state()?.let {
                                controller.seekTo(
                                    (it.positionMs + 10_000).coerceAtMost(
                                        it.durationMs ?: Long.MAX_VALUE,
                                    ),
                                )
                            }
                        }) { Text("+10s") }
                    }
                }
                if (state?.progressSaveFailed == true) Text("Progress could not be saved.")
            }
        }
    }
}

private fun ResolutionFailure.message(): String = when (this) {
    ResolutionFailure.ACCESS_LOST -> "Folder access was lost. Authorize the folder from Library."
    ResolutionFailure.NO_SOURCE -> "No source is available for this video."
    ResolutionFailure.NOT_FOUND -> "The video could not be found."
    ResolutionFailure.UNSUPPORTED_REPRESENTATION -> "This source is not a supported video."
    ResolutionFailure.UNAVAILABLE -> "The source is unavailable."
    ResolutionFailure.TRANSIENT_PROVIDER_FAILURE -> "The source could not be read. Try again."
}

private fun PlaybackFailure?.message(): String = when (this) {
    PlaybackFailure.ACCESS_LOST -> "Playback lost access to the video."
    PlaybackFailure.NOT_FOUND -> "Playback could no longer find the video."
    PlaybackFailure.UNSUPPORTED -> "The video format or decoder is not supported."
    PlaybackFailure.DISCONNECTED -> "The playback connection was lost."
    PlaybackFailure.UNAVAILABLE, null -> "The video could not be played."
}
