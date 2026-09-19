package app.universalmedia.playback.media3

import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.playback.api.PlaybackCheckpoint
import app.universalmedia.playback.api.PlaybackProgressSink
import app.universalmedia.playback.api.PlaybackProvenance
import app.universalmedia.playback.api.PlaybackRequest
import app.universalmedia.source.api.ResolvedVideo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCheckpointWriterTest {
    @Test fun delayedWriteFinishesBeforeNewerBackwardSeek() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<Long>()
        val writer = PlaybackCheckpointWriter(
            PlaybackProgressSink {
                if (it.expectedRevision == 0L) {
                    entered.complete(Unit)
                    release.await()
                }
                writes.add(it.positionMs)
                it.expectedRevision + 1
            },
        )
        val run = PlaybackRun(request)
        val consumer = launch {
            writer.write(run, sample(900))
            writer.write(run, sample(100))
        }
        entered.await()
        assertEquals(emptyList<Long>(), writes)
        release.complete(Unit)
        consumer.join()
        assertEquals(listOf(900L, 100L), writes)
        assertEquals(2L, run.revision)
    }

    private val request = PlaybackRequest(
        ConsumptionTargetRef.MediaTarget(MediaId.generate()),
        ResolvedVideo("content://fixture/video", "video/mp4"),
        PlaybackProvenance(SourceBindingId.generate(), AssetId.generate(), 0),
    )

    @Test fun queuedBackwardSeekUsesRevisionFromLastAcceptedWrite() = runBlocking {
        val written = mutableListOf<PlaybackCheckpoint>()
        val writer = PlaybackCheckpointWriter(
            PlaybackProgressSink {
                written.add(it)
                it.expectedRevision + 1
            },
        )
        val run = PlaybackRun(request)
        writer.write(run, sample(900))
        writer.write(run, sample(100))
        assertEquals(listOf(0L, 1L), written.map { it.expectedRevision })
        assertEquals(listOf(900L, 100L), written.map { it.positionMs })
    }

    @Test fun staleRunDoesNotRetryOrAffectReplacementRun() = runBlocking {
        var writes = 0
        val writer = PlaybackCheckpointWriter(
            PlaybackProgressSink {
                writes++
                null
            },
        )
        val run = PlaybackRun(request)
        writer.write(run, sample(900))
        writer.write(run, sample(100))
        writer.write(PlaybackRun(request), sample(200))
        assertEquals(2, writes)
    }

    @Test fun sinkFailureIsContainedAndRunIsNotRetried() = runBlocking {
        var writes = 0
        val writer = PlaybackCheckpointWriter(
            PlaybackProgressSink {
                writes++
                error("database unavailable")
            },
        )
        val run = PlaybackRun(request)
        writer.write(run, sample(900))
        writer.write(run, sample(100))
        assertEquals(1, writes)
    }

    private fun sample(position: Long) = PlaybackCheckpoint(request, position, 1000, false, 1, 0)
}
