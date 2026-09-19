package app.universalmedia.core.domain

import app.universalmedia.core.model.ConsumptionTargetRef

sealed interface VideoResumeDecision {
    data class Exact(val anchor: VideoResumeAnchor) : VideoResumeDecision
    data object StartFromBeginning : VideoResumeDecision
}

/** Local video timestamps are portable only across the same representation revision. */
fun selectVideoResume(
    target: ConsumptionTargetRef,
    progress: VideoProgressState?,
    resolvedContext: VideoResumeContext?,
): VideoResumeDecision {
    val anchor = progress?.anchor ?: return VideoResumeDecision.StartFromBeginning
    val previous = progress.context ?: return VideoResumeDecision.StartFromBeginning
    return if (
        progress.target == target && resolvedContext != null &&
        previous.assetId == resolvedContext.assetId &&
        previous.assetRevision == resolvedContext.assetRevision
    ) {
        VideoResumeDecision.Exact(anchor)
    } else {
        VideoResumeDecision.StartFromBeginning
    }
}
