package app.openstory.reader.routing

import app.openstory.common.id.PluginId
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.engine.penalizesSourceHealth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSourceExecutionLimiterTest {
    private val sourceId = PluginId("source")

    @Test
    fun onlyOneHalfOpenProbeLeasePerSourceOperationKeyCanBeHeld() {
        val limiter = ReaderSourceExecutionLimiter()
        val key = SourceOperationKey(sourceId)

        val first = assertNotNull(limiter.tryAcquireHalfOpenProbe(key))
        assertNull(limiter.tryAcquireHalfOpenProbe(key))
        first.release()
        val next = assertNotNull(limiter.tryAcquireHalfOpenProbe(key))
        next.release()
    }

    @Test
    fun unusedProbeLeaseReleaseIsIdempotent() {
        val limiter = ReaderSourceExecutionLimiter()
        val key = SourceOperationKey(sourceId)
        val lease = assertNotNull(limiter.tryAcquireHalfOpenProbe(key))

        lease.release()
        lease.release()

        assertNotNull(limiter.tryAcquireHalfOpenProbe(key)).release()
    }

    @Test
    fun onlyOneReaderRemoteAttemptPerSourceIsActiveAcrossCallers() = runTest {
        val limiter = ReaderSourceExecutionLimiter()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var active = 0
        var maximumActive = 0

        val first = launch {
            limiter.withRemotePermit(sourceId, ReaderRemoteWorkPriority.FOREGROUND) {
                active++
                maximumActive = maxOf(maximumActive, active)
                firstEntered.complete(Unit)
                releaseFirst.await()
                active--
            }
        }
        firstEntered.await()
        val second = launch {
            limiter.withRemotePermit(sourceId, ReaderRemoteWorkPriority.FOREGROUND) {
                active++
                maximumActive = maxOf(maximumActive, active)
                active--
            }
        }
        runCurrent()
        assertEquals(1, maximumActive)

        releaseFirst.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, maximumActive)
    }

    @Test
    fun atMostTwoForegroundRemoteAttemptsAreActiveProcessWide() = runTest {
        val limiter = ReaderSourceExecutionLimiter()
        val release = CompletableDeferred<Unit>()
        val entered = Channel<Unit>(Channel.UNLIMITED)
        var active = 0
        var maximumActive = 0

        val jobs = (0 until 4).map { index ->
            launch {
                limiter.withRemotePermit(
                    PluginId("source-$index"),
                    ReaderRemoteWorkPriority.FOREGROUND,
                ) {
                    active += 1
                    maximumActive = maxOf(maximumActive, active)
                    entered.send(Unit)
                    release.await()
                    active -= 1
                }
            }
        }

        repeat(2) { entered.receive() }
        runCurrent()
        assertEquals(2, maximumActive)
        assertEquals(2, active)

        release.complete(Unit)
        jobs.forEach { it.join() }
        assertEquals(2, maximumActive)
    }

    @Test
    fun cancellingForegroundWaitingForGlobalPermitDoesNotLeakItsSourceLane() = runTest {
        val limiter = ReaderSourceExecutionLimiter()
        val release = CompletableDeferred<Unit>()
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val holders = listOf("a", "b").map { suffix ->
            launch {
                limiter.withRemotePermit(PluginId("source-$suffix"), ReaderRemoteWorkPriority.FOREGROUND) {
                    entered.send(Unit)
                    release.await()
                }
            }
        }
        repeat(2) { entered.receive() }

        val waitingEntered = CompletableDeferred<Unit>()
        val waiting = launch {
            limiter.withRemotePermit(PluginId("source-waiting"), ReaderRemoteWorkPriority.FOREGROUND) {
                waitingEntered.complete(Unit)
            }
        }
        runCurrent()
        assertFalse(waitingEntered.isCompleted)
        waiting.cancel()
        waiting.join()

        release.complete(Unit)
        holders.forEach { it.join() }
        var freshEntered = false
        limiter.withRemotePermit(PluginId("source-waiting"), ReaderRemoteWorkPriority.FOREGROUND) {
            freshEntered = true
        }
        assertEquals(true, freshEntered)
    }


    @Test
    fun onlyOneRemotePrefetchIsActiveProcessWideAcrossDifferentSources() = runTest {
        val limiter = ReaderSourceExecutionLimiter()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var active = 0
        var maximumActive = 0

        val first = launch {
            limiter.withRemotePermit(PluginId("source-a"), ReaderRemoteWorkPriority.PREFETCH) {
                active++
                maximumActive = maxOf(maximumActive, active)
                firstEntered.complete(Unit)
                releaseFirst.await()
                active--
            }
        }
        firstEntered.await()
        val second = launch {
            limiter.withRemotePermit(PluginId("source-b"), ReaderRemoteWorkPriority.PREFETCH) {
                active++
                maximumActive = maxOf(maximumActive, active)
                active--
            }
        }
        runCurrent()

        assertEquals(1, maximumActive)
        releaseFirst.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, maximumActive)
    }

    @Test
    fun foregroundPreemptsSameSourcePrefetchWaitingForGlobalPrefetchPermit() = runTest {
        val limiter = ReaderSourceExecutionLimiter()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val first = launch {
            limiter.withRemotePermit(PluginId("source-a"), ReaderRemoteWorkPriority.PREFETCH) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val waitingPrefetch = async {
            try {
                limiter.withRemotePermit(PluginId("source-b"), ReaderRemoteWorkPriority.PREFETCH) {
                    error("Preempted prefetch must not enter its work block.")
                }
                null
            } catch (cancelled: CancellationException) {
                cancelled
            }
        }
        runCurrent()
        val foreground = async {
            limiter.withRemotePermit(PluginId("source-b"), ReaderRemoteWorkPriority.FOREGROUND) { "foreground" }
        }

        assertEquals("foreground", foreground.await())
        assertIs<ReaderPrefetchPreemptedException>(waitingPrefetch.await())
        releaseFirst.complete(Unit)
        first.join()

        var freshEntered = false
        limiter.withRemotePermit(PluginId("source-b"), ReaderRemoteWorkPriority.PREFETCH) {
            freshEntered = true
        }
        assertEquals(true, freshEntered)
    }

    @Test
    fun distinctSourcesAllowTwoForegroundAndOnePrefetchAtTheSameTime() = runTest {
        val limiter = ReaderSourceExecutionLimiter()
        val release = CompletableDeferred<Unit>()
        val entered = Channel<ReaderRemoteWorkPriority>(Channel.UNLIMITED)
        val jobs = listOf(
            PluginId("foreground-a") to ReaderRemoteWorkPriority.FOREGROUND,
            PluginId("foreground-b") to ReaderRemoteWorkPriority.FOREGROUND,
            PluginId("prefetch") to ReaderRemoteWorkPriority.PREFETCH,
        ).map { (source, priority) ->
            launch {
                limiter.withRemotePermit(source, priority) {
                    entered.send(priority)
                    release.await()
                }
            }
        }

        val activePriorities = List(3) { entered.receive() }
        assertEquals(2, activePriorities.count { it == ReaderRemoteWorkPriority.FOREGROUND })
        assertEquals(1, activePriorities.count { it == ReaderRemoteWorkPriority.PREFETCH })
        release.complete(Unit)
        jobs.forEach { it.join() }
    }

    @Test
    fun queuedForegroundWinsOverQueuedPrefetch() = runTest {
        val limiter = ReaderSourceExecutionLimiter()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val owner = launch {
            limiter.withRemotePermit(sourceId, ReaderRemoteWorkPriority.FOREGROUND) {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val prefetch = launch {
            limiter.withRemotePermit(sourceId, ReaderRemoteWorkPriority.PREFETCH) { order += "prefetch" }
        }
        val foreground = launch {
            limiter.withRemotePermit(sourceId, ReaderRemoteWorkPriority.FOREGROUND) { order += "foreground" }
        }
        runCurrent()
        release.complete(Unit)
        owner.join()
        foreground.join()
        prefetch.join()

        assertEquals(listOf("foreground", "prefetch"), order)
    }

    @Test
    fun foregroundPreemptsActivePrefetchWithTypedNonPenalizingCancellation() = runTest {
        val limiter = ReaderSourceExecutionLimiter()
        val prefetchEntered = CompletableDeferred<Unit>()
        val prefetch = async {
            try {
                limiter.withRemotePermit(sourceId, ReaderRemoteWorkPriority.PREFETCH) {
                    prefetchEntered.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
                null
            } catch (cancelled: CancellationException) {
                cancelled
            }
        }
        prefetchEntered.await()
        val foreground = async {
            limiter.withRemotePermit(sourceId, ReaderRemoteWorkPriority.FOREGROUND) { "foreground" }
        }

        assertEquals("foreground", foreground.await())
        assertIs<ReaderPrefetchPreemptedException>(prefetch.await())
        assertEquals(false, SourceObservation.Cancellation.PrefetchPreempted.penalizesSourceHealth)
    }
}
