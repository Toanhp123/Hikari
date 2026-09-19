package app.universalmedia

import app.universalmedia.core.domain.ProgressStore
import app.universalmedia.core.domain.ProgressWriteResult
import app.universalmedia.core.domain.VideoProgressCheckpoint
import app.universalmedia.core.domain.VideoProgressState
import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.playback.api.PlaybackCheckpoint
import app.universalmedia.playback.api.PlaybackProvenance
import app.universalmedia.playback.api.PlaybackRequest
import app.universalmedia.source.api.ResolvedVideo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackProgressAdapterTest {
    @Test fun mapsRuntimeEventToCanonicalProgressWithoutPersistingUri() = runBlocking {
        val store = Store()
        val request = PlaybackRequest(
            ConsumptionTargetRef.MediaTarget(MediaId.generate()),
            ResolvedVideo("content://private/video", "video/mp4"),
            PlaybackProvenance(SourceBindingId.generate(), AssetId.generate(), 4),
        )
        val result = PlaybackProgressAdapter(store).checkpoint(
            PlaybackCheckpoint(request, 12, 100, false, 50, 7),
        )
        assertNull(result)
        val saved = requireNotNull(store.saved)
        assertEquals(request.target, saved.target)
        assertEquals(request.provenance.assetId, saved.context.assetId)
        assertEquals(4L, saved.context.assetRevision.value)
        assertEquals(7L, saved.expectedStateRevision)
        assertEquals(12L, saved.anchor.positionMs)
    }

    private class Store : ProgressStore {
        var saved: VideoProgressCheckpoint? = null
        override suspend fun load(target: ConsumptionTargetRef): VideoProgressState? = null
        override suspend fun checkpointVideo(
            checkpoint: VideoProgressCheckpoint,
        ): ProgressWriteResult {
            saved = checkpoint
            return ProgressWriteResult.Stale
        }
        override suspend fun markVideoCompleted(
            target: ConsumptionTargetRef,
            expectedStateRevision: Long,
            observedAtEpochMs: Long,
        ): ProgressWriteResult = error("unused")
    }
}
