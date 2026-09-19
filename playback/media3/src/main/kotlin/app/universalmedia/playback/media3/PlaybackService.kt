package app.universalmedia.playback.media3

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import app.universalmedia.playback.api.PlaybackCheckpoint
import app.universalmedia.playback.api.PlaybackRuntimeDependenciesProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private var current: PlaybackRun? = null
    private var periodic: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val checkpoints = Channel<Pair<PlaybackRun, PlaybackCheckpoint>>(64)

    override fun onCreate() {
        super.onCreate()
        val provider = application as PlaybackRuntimeDependenciesProvider
        val dependencies = provider.playbackDependencies
        val writer = PlaybackCheckpointWriter(dependencies.progressSink)
        scope.launch {
            for ((run, checkpoint) in checkpoints) {
                val finished = withTimeoutOrNull(5000) {
                    writer.write(run, checkpoint)
                    true
                }
                if (finished == null) run.writable = false
                if (!run.writable && current === run) reportProgressFailure()
            }
            scope.cancel()
        }
        val engine = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady) checkpoint()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) current?.established = true
                    if (playbackState == Player.STATE_ENDED) {
                        current?.completed = true
                        checkpoint()
                    }
                    if (playbackState == Player.STATE_IDLE) checkpoint()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) checkpoint()
                }
            })
        }
        player = engine
        session = MediaSession.Builder(this, engine).setCallback(Callback()).build()
        periodic = scope.launch {
            while (true) {
                delay(PlaybackCheckpointPolicy.INTERVAL_MS)
                if (engine.isPlaying) checkpoint(immediate = false)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session?.takeIf { controllerInfo.uid == Process.myUid() }

    override fun onDestroy() {
        periodic?.cancel()
        checkpoint()
        current = null
        session?.release()
        session = null
        player?.release()
        player = null
        checkpoints.close()
        // Drain accepted boundaries while this process is alive; never block the main thread.
        scope.launch {
            delay(10_000)
            scope.cancel()
        }
        super.onDestroy()
    }

    private fun checkpoint(immediate: Boolean = true) {
        val run = current ?: return
        val engine = player ?: return
        if (!run.established || !run.writable) return
        val position = engine.currentPosition.coerceAtLeast(0)
        if (!run.policy.shouldCheckpoint(
                SystemClock.elapsedRealtime(),
                position,
                run.completed,
                immediate,
            )
        ) {
            return
        }
        val accepted = checkpoints.trySend(
            run to PlaybackCheckpoint(
                run.request,
                position,
                engine.duration.takeIf { it >= 0 },
                run.completed,
                System.currentTimeMillis(),
                run.revision,
            ),
        )
        if (accepted.isFailure) {
            run.writable = false
            reportProgressFailure()
        }
    }

    private fun reportProgressFailure() {
        session?.setSessionExtras(Bundle().apply { putBoolean(PROGRESS_SAVE_FAILED, true) })
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (controller.uid != Process.myUid()) return MediaSession.ConnectionResult.reject()
            val commands = Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA,
                    Player.COMMAND_SET_VIDEO_SURFACE,
                ).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder()
                .setAvailablePlayerCommands(commands)
                .setAvailableSessionCommands(
                    SessionCommands.Builder()
                        .add(SessionCommand(PlaybackWire.START, Bundle.EMPTY)).build(),
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (
                controller.uid != Process.myUid() ||
                customCommand.customAction != PlaybackWire.START
            ) {
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED),
                )
            }
            val request = try {
                PlaybackWire.decode(args)
            } catch (_: IllegalArgumentException) {
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE),
                )
            }
            checkpoint()
            current = null
            session.player.stop()
            session.setSessionExtras(Bundle.EMPTY)
            current = PlaybackRun(request)
            session.player.apply {
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(request.content.contentUri)
                        .setMimeType(request.content.mimeType).build(),
                    request.initialPositionMs,
                )
                prepare()
                play()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    internal companion object {
        const val PROGRESS_SAVE_FAILED = "progressSaveFailed"
    }
}
