package app.universalmedia.playback.media3

import app.universalmedia.playback.api.PlaybackCheckpoint
import app.universalmedia.playback.api.PlaybackProgressSink
import app.universalmedia.playback.api.PlaybackRequest
import kotlinx.coroutines.CancellationException

internal class PlaybackRun(val request: PlaybackRequest) {
    var revision = request.expectedProgressRevision
    var writable = true
    val policy = PlaybackCheckpointPolicy()
    var completed = false
    var established = false
}

/** Called by one service consumer; revisions are chosen at write time, never at enqueue time. */
internal class PlaybackCheckpointWriter(private val sink: PlaybackProgressSink) {
    suspend fun write(run: PlaybackRun, checkpoint: PlaybackCheckpoint) {
        if (!run.writable) return
        val revision = try {
            sink.checkpoint(checkpoint.copy(expectedRevision = run.revision))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed/stale run cannot invent authority by rereading and retrying.
            null
        }
        if (revision == null) run.writable = false else run.revision = revision
    }
}
