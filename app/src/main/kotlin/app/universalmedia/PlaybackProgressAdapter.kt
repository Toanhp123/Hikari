package app.universalmedia

import app.universalmedia.core.domain.AssetRevision
import app.universalmedia.core.domain.CompletionState
import app.universalmedia.core.domain.ProgressStore
import app.universalmedia.core.domain.ProgressWriteResult
import app.universalmedia.core.domain.VideoProgressCheckpoint
import app.universalmedia.core.domain.VideoResumeAnchor
import app.universalmedia.core.domain.VideoResumeContext
import app.universalmedia.playback.api.PlaybackCheckpoint
import app.universalmedia.playback.api.PlaybackProgressSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PlaybackProgressAdapter(private val store: ProgressStore) : PlaybackProgressSink {
    override suspend fun checkpoint(checkpoint: PlaybackCheckpoint): Long? =
        withContext(Dispatchers.IO) {
            val provenance = checkpoint.request.provenance
            val previous = store.load(checkpoint.request.target)
            val result = store.checkpointVideo(
                VideoProgressCheckpoint(
                    checkpoint.request.target,
                    VideoResumeAnchor(checkpoint.positionMs, checkpoint.durationMs),
                    if (checkpoint.completed || previous?.completion == CompletionState.COMPLETED) {
                        CompletionState.COMPLETED
                    } else {
                        CompletionState.IN_PROGRESS
                    },
                    VideoResumeContext(
                        provenance.bindingId,
                        provenance.assetId,
                        AssetRevision(provenance.assetRevision),
                    ),
                    checkpoint.observedAtEpochMs,
                    checkpoint.expectedRevision,
                ),
            )
            (result as? ProgressWriteResult.Applied)?.state?.stateRevision
        }
}
