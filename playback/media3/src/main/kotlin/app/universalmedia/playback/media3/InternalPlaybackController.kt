package app.universalmedia.playback.media3

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.SurfaceView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import app.universalmedia.playback.api.PlaybackController
import app.universalmedia.playback.api.PlaybackFailure
import app.universalmedia.playback.api.PlaybackPhase
import app.universalmedia.playback.api.PlaybackRequest
import app.universalmedia.playback.api.PlaybackState
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Main-thread adapter. Closing a screen disconnects its controller, not the service's player. */
@UnstableApi
class InternalPlaybackController private constructor(private val controller: MediaController) :
    PlaybackController {
    private var closed = false
    private var surface: SurfaceView? = null

    override suspend fun start(request: PlaybackRequest): Boolean {
        if (closed || !controller.isConnected) return false
        return controller.sendCustomCommand(
            SessionCommand(PlaybackWire.START, Bundle.EMPTY),
            PlaybackWire.encode(request),
        )
            .awaitResult().resultCode == SessionResult.RESULT_SUCCESS
    }

    override fun play() {
        controller.play()
    }
    override fun pause() {
        controller.pause()
    }
    override fun seekTo(positionMs: Long) {
        require(positionMs >= 0)
        controller.seekTo(positionMs)
    }
    override fun stop() {
        controller.pause()
        controller.stop()
    }

    /** Android View only; Media3 types never cross the app/feature UI boundary. */
    fun attachVideoSurface(view: SurfaceView?) {
        surface?.let(controller::clearVideoSurfaceView)
        surface = view
        view?.let(controller::setVideoSurfaceView)
    }

    override fun state(): PlaybackState {
        if (closed || !controller.isConnected) {
            return PlaybackState(
                PlaybackPhase.FAILED,
                false,
                0,
                null,
                PlaybackFailure.DISCONNECTED,
            )
        }
        val failure = controller.playerError?.let {
            when (it.errorCode) {
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackFailure.ACCESS_LOST

                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackFailure.NOT_FOUND

                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                -> {
                    PlaybackFailure.UNSUPPORTED
                }

                else -> PlaybackFailure.UNAVAILABLE
            }
        }
        val phase = if (failure != null) {
            PlaybackPhase.FAILED
        } else {
            when (controller.playbackState) {
                Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
                Player.STATE_READY -> PlaybackPhase.READY
                Player.STATE_ENDED -> PlaybackPhase.ENDED
                else -> PlaybackPhase.IDLE
            }
        }
        return PlaybackState(
            phase,
            controller.isPlaying,
            controller.currentPosition.coerceAtLeast(0),
            controller.duration.takeIf { it >= 0 },
            failure,
        )
    }

    override fun close() {
        if (closed) return
        attachVideoSurface(null)
        closed = true
        controller.release()
    }

    companion object {
        suspend fun connect(context: Context): InternalPlaybackController {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context.applicationContext, token).buildAsync()
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
                future.addListener({
                    try {
                        val connection = InternalPlaybackController(future.get())
                        continuation.resume(connection) { _, value, _ ->
                            Handler(connection.controller.applicationLooper).post { value.close() }
                        }
                    } catch (exception: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                }, Executor { it.run() })
            }
        }
    }
}

private suspend fun <T> ListenableFuture<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel(false) }
        addListener({
            try {
                continuation.resume(get())
            } catch (exception: Exception) {
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
        }, Executor { it.run() })
    }
