package app.universalmedia.playback.media3

internal class PlaybackCheckpointPolicy {
    private var lastPosition = 0L
    private var lastCompleted = false
    private var lastCheckpointTime: Long? = null

    fun shouldCheckpoint(
        nowMs: Long,
        positionMs: Long,
        completed: Boolean,
        immediate: Boolean,
    ): Boolean {
        if (positionMs == lastPosition && completed == lastCompleted) return false
        val previousTime = lastCheckpointTime
        if (!immediate && previousTime != null && nowMs - previousTime < INTERVAL_MS) return false
        lastPosition = positionMs
        lastCompleted = completed
        if (!immediate) lastCheckpointTime = nowMs
        return true
    }

    companion object {
        const val INTERVAL_MS = 5000L
    }
}
