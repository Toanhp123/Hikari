package app.universalmedia

import app.universalmedia.core.domain.AssetRevision
import app.universalmedia.core.domain.CompletionState
import app.universalmedia.core.domain.ProgressStore
import app.universalmedia.core.domain.ProgressWriteResult
import app.universalmedia.core.domain.VideoProgressCheckpoint
import app.universalmedia.core.domain.VideoProgressState
import app.universalmedia.core.domain.VideoResumeAnchor
import app.universalmedia.core.domain.VideoResumeContext
import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.playback.api.PlaybackController
import app.universalmedia.playback.api.PlaybackFailure
import app.universalmedia.playback.api.PlaybackPhase
import app.universalmedia.playback.api.PlaybackRequest
import app.universalmedia.playback.api.PlaybackState
import app.universalmedia.source.api.ResolutionFailure
import app.universalmedia.source.api.ResolvedVideo
import app.universalmedia.source.api.SourceResolution
import app.universalmedia.source.api.SourceResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorTest {
    private val media = MediaId.generate()
    private val target = ConsumptionTargetRef.MediaTarget(media)
    private val provenance =
        VideoResumeContext(SourceBindingId.generate(), AssetId.generate(), AssetRevision(1))
    private val ready = SourceResolution.Ready(
        target,
        provenance,
        ResolvedVideo("content://fixture/video", "video/mp4"),
    )

    @Test fun backDuringResolutionPausesLateStart() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val player = Player()
        val coordinator = PlaybackCoordinator(
            Store(),
            SourceResolver {
                entered.complete(Unit)
                release.await()
                ready
            },
        ) { player }
        val opening = async { coordinator.open(media) }
        entered.await()
        coordinator.leave()
        release.complete(Unit)
        assertEquals(PlayerOpenResult.Ready, opening.await())
        assertFalse(player.state().isPlaying)
    }

    @Test fun acceptedStartIsNotRepeatedWhileControllerStillReportsIdle() = runBlocking {
        val player = Player()
        val coordinator = PlaybackCoordinator(Store(), SourceResolver { ready }) { player }
        coordinator.open(media)
        player.snapshot = player.snapshot.copy(phase = PlaybackPhase.IDLE)
        coordinator.open(media)
        assertEquals(1, player.starts)
    }

    @Test fun backAlsoPausesAnOpenQueuedBehindAnotherTarget() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val player = Player()
        var resolutions = 0
        val coordinator = PlaybackCoordinator(
            Store(),
            SourceResolver { requested ->
                if (resolutions++ == 0) {
                    entered.complete(Unit)
                    release.await()
                }
                ready.copy(target = requested)
            },
        ) { player }
        val first = async { coordinator.open(media) }
        entered.await()
        val second = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            coordinator.open(MediaId.generate())
        }
        coordinator.leave()
        release.complete(Unit)
        first.await()
        second.await()
        assertFalse(player.state().isPlaying)
    }

    @Test fun loadsThenResolvesThenStartsWithCompatibleProgress() = runBlocking {
        val events = mutableListOf<String>()
        val store = Store { events += "load" }
        store.saved =
            VideoProgressState(
                target,
                VideoResumeAnchor(4200),
                CompletionState.IN_PROGRESS,
                provenance,
                7,
                1,
            )
        val player = Player { events += "start" }
        val coordinator =
            PlaybackCoordinator(
                store,
                SourceResolver {
                    events += "resolve"
                    ready
                },
            ) { player }
        assertEquals(PlayerOpenResult.Ready, coordinator.open(media))
        assertEquals(listOf("load", "resolve", "start"), events)
        assertEquals(4200L, player.request!!.initialPositionMs)
        assertEquals(7L, player.request!!.expectedProgressRevision)
        assertEquals(0, store.writes)
    }

    @Test fun accessFailurePreservesProgressAndDoesNotConnect() = runBlocking {
        val store = Store()
        val previous =
            VideoProgressState(
                target,
                VideoResumeAnchor(4200),
                CompletionState.IN_PROGRESS,
                provenance,
                7,
                1,
            )
        store.saved = previous
        val coordinator =
            PlaybackCoordinator(
                store,
                SourceResolver {
                    SourceResolution.Failed(ResolutionFailure.ACCESS_LOST)
                },
            ) { error("must not connect") }
        assertEquals(
            PlayerOpenResult.SourceFailed(ResolutionFailure.ACCESS_LOST),
            coordinator.open(media),
        )
        assertEquals(previous, store.saved)
        assertEquals(0, store.writes)
    }

    @Test fun recreatedScreenAndReturnToSameCardReuseSessionWithoutRestart() = runBlocking {
        val player = Player()
        var resolutions = 0
        var connections = 0
        val coordinator =
            PlaybackCoordinator(
                Store(),
                SourceResolver {
                    resolutions++
                    ready
                },
            ) {
                connections++
                player
            }
        coordinator.open(media)
        player.seekTo(9000)
        player.pause()
        coordinator.open(media)
        assertEquals(1, resolutions)
        assertEquals(1, connections)
        assertEquals(1, player.starts)
        assertEquals(9000L, player.state().positionMs)
        assertFalse(player.state().isPlaying)
    }

    @Test fun changedRepresentationStartsAtZeroAndRetainsRevision() = runBlocking {
        val store = Store()
        store.saved =
            VideoProgressState(
                target,
                VideoResumeAnchor(4200),
                CompletionState.IN_PROGRESS,
                provenance.copy(assetRevision = AssetRevision(0)),
                7,
                1,
            )
        val player = Player()
        val coordinator = PlaybackCoordinator(store, SourceResolver { ready }) { player }
        coordinator.open(media)
        assertEquals(0L, player.request!!.initialPositionMs)
        assertEquals(7L, player.request!!.expectedProgressRevision)
    }

    @Test fun disconnectedSessionClosesOldControllerAndResolvesAgain() = runBlocking {
        val first = Player()
        val second = Player()
        var connections = 0
        val coordinator =
            PlaybackCoordinator(Store(), SourceResolver { ready }) {
                if (connections++ ==
                    0
                ) {
                    first
                } else {
                    second
                }
            }
        coordinator.open(media)
        first.snapshot =
            first.snapshot.copy(
                phase = PlaybackPhase.FAILED,
                failure = PlaybackFailure.DISCONNECTED,
            )
        coordinator.open(media)
        assertTrue(first.closed)
        assertEquals(1, second.starts)
    }

    private class Store(private val onLoad: () -> Unit = {}) : ProgressStore {
        var saved: VideoProgressState? = null
        var writes = 0
        override suspend fun load(target: ConsumptionTargetRef): VideoProgressState? {
            onLoad()
            return saved
        }
        override suspend fun checkpointVideo(
            checkpoint: VideoProgressCheckpoint,
        ): ProgressWriteResult {
            writes++
            return ProgressWriteResult.Stale
        }
        override suspend fun markVideoCompleted(
            target: ConsumptionTargetRef,
            expectedStateRevision: Long,
            observedAtEpochMs: Long,
        ): ProgressWriteResult = error("unused")
    }

    private class Player(private val onStart: () -> Unit = {}) : PlaybackController {
        var request: PlaybackRequest? = null
        var starts = 0
        var closed = false
        var snapshot = PlaybackState(PlaybackPhase.IDLE, false, 0, 30000)
        override suspend fun start(request: PlaybackRequest): Boolean {
            onStart()
            this.request =
                request
            starts++
            snapshot =
                snapshot.copy(phase = PlaybackPhase.READY, isPlaying = true)
            return true
        }
        override fun play() {
            snapshot = snapshot.copy(isPlaying = true)
        }
        override fun pause() {
            snapshot = snapshot.copy(isPlaying = false)
        }
        override fun seekTo(positionMs: Long) {
            snapshot = snapshot.copy(positionMs = positionMs)
        }
        override fun stop() {
            snapshot = snapshot.copy(phase = PlaybackPhase.IDLE)
        }
        override fun state(): PlaybackState = snapshot
        override fun close() {
            closed = true
        }
    }
}
