package app.universalmedia.core.domain

import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.SourceBindingId

@JvmInline
value class AssetRevision(val value: Long) {
    init {
        require(value >= 0)
    }
}

enum class CompletionState { IN_PROGRESS, COMPLETED }

data class VideoResumeAnchor(val positionMs: Long, val durationSnapshotMs: Long? = null) {
    init {
        require(positionMs >= 0)
        require(durationSnapshotMs == null || durationSnapshotMs >= 0)
    }
}

// Provenance only: progress belongs to the canonical target, never to this asset.
data class VideoResumeContext(
    val bindingId: SourceBindingId,
    val assetId: AssetId,
    val assetRevision: AssetRevision,
)

data class VideoProgressCheckpoint(
    val target: ConsumptionTargetRef,
    val anchor: VideoResumeAnchor,
    val completion: CompletionState,
    val context: VideoResumeContext,
    val observedAtEpochMs: Long,
    val expectedStateRevision: Long,
) {
    init {
        require(expectedStateRevision >= 0)
    }
}

data class VideoProgressState(
    val target: ConsumptionTargetRef,
    val anchor: VideoResumeAnchor?,
    val completion: CompletionState,
    val context: VideoResumeContext?,
    val stateRevision: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(stateRevision > 0)
        require((anchor == null) == (context == null))
    }
}

sealed interface ProgressWriteResult {
    data class Applied(val state: VideoProgressState) : ProgressWriteResult

    data object Stale : ProgressWriteResult
}

interface ProgressStore {
    suspend fun load(target: ConsumptionTargetRef): VideoProgressState?

    /** Atomic compare-and-set: absent state has revision 0; accepted writes increment revision.
     * Callers serialize checkpoints; a stale checkpoint must not be retried as a newer event.
     * Position is never used for ordering, and completion is never inferred from duration.
     */
    suspend fun checkpointVideo(checkpoint: VideoProgressCheckpoint): ProgressWriteResult

    /** Uses the same revision check; retains any existing anchor/context, permits no anchor. */
    suspend fun markVideoCompleted(
        target: ConsumptionTargetRef,
        expectedStateRevision: Long,
        observedAtEpochMs: Long,
    ): ProgressWriteResult
}
