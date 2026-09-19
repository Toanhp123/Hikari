package app.universalmedia.playback.media3

import android.os.Bundle
import android.os.Process
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private var current: PlaybackRun? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val checkpoints = Channel<Pair<PlaybackRun, PlaybackCheckpoint>>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        val provider = application as PlaybackRuntimeDependenciesProvider
        val dependencies = provider.playbackDependencies
        val writer = PlaybackCheckpointWriter(dependencies.progressSink)
        scope.launch {
            for ((run, checkpoint) in checkpoints) {
                writer.write(run, checkpoint)
            }
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
            })
        }
        player = engine
        session = MediaSession.Builder(this, engine).setCallback(Callback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session?.takeIf { controllerInfo.uid == Process.myUid() }

    override fun onDestroy() {
        session?.release()
        session = null
        player?.release()
        player = null
        current = null
        checkpoints.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun checkpoint() {
        val run = current ?: return
        val engine = player ?: return
        checkpoints.trySend(
            run to PlaybackCheckpoint(
                run.request,
                engine.currentPosition.coerceAtLeast(0),
                engine.duration.takeIf { it >= 0 },
                false,
                System.currentTimeMillis(),
                run.revision,
            ),
        )
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
}
