package app.universalmedia.playback.api

import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.source.api.ResolvedVideo

/** Runtime provenance; it never changes ownership of progress from the canonical target. */
data class PlaybackProvenance(
    val bindingId: SourceBindingId,
    val assetId: AssetId,
    val assetRevision: Long,
) {
    init {
        require(assetRevision >= 0)
    }
}

data class PlaybackRequest(
    val target: ConsumptionTargetRef,
    val content: ResolvedVideo,
    val provenance: PlaybackProvenance,
    val initialPositionMs: Long = 0,
    val expectedProgressRevision: Long = 0,
) {
    init {
        require(initialPositionMs >= 0)
        require(expectedProgressRevision >= 0)
    }
}

enum class PlaybackPhase { IDLE, BUFFERING, READY, ENDED, FAILED }

enum class PlaybackFailure { ACCESS_LOST, NOT_FOUND, UNSUPPORTED, UNAVAILABLE, DISCONNECTED }

data class PlaybackState(
    val phase: PlaybackPhase,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
    val failure: PlaybackFailure? = null,
)

interface PlaybackController : AutoCloseable {
    suspend fun start(request: PlaybackRequest): Boolean
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()
    fun state(): PlaybackState
}

data class PlaybackCheckpoint(
    val request: PlaybackRequest,
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
    val observedAtEpochMs: Long,
    val expectedRevision: Long,
)

/** Returns the accepted durable revision, or null for a stale write. */
fun interface PlaybackProgressSink {
    suspend fun checkpoint(checkpoint: PlaybackCheckpoint): Long?
}

data class PlaybackRuntimeDependencies(val progressSink: PlaybackProgressSink)

interface PlaybackRuntimeDependenciesProvider {
    val playbackDependencies: PlaybackRuntimeDependencies
}
