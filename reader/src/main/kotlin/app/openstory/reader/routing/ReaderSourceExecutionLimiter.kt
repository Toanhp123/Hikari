package app.openstory.reader.routing

import app.openstory.common.id.PluginId
import app.openstory.reader.engine.SourceOperationKey
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal enum class ReaderRemoteWorkPriority {
    FOREGROUND,
    PREFETCH,
}

internal class ReaderPrefetchPreemptedException : CancellationException(
    "Reader prefetch was preempted by foreground work for the same source.",
)

internal class ReaderHalfOpenProbeLease(
    internal val key: SourceOperationKey,
    private val onRelease: (SourceOperationKey) -> Unit,
) {
    private val released = AtomicBoolean(false)

    fun release() {
        if (released.compareAndSet(false, true)) onRelease(key)
    }
}

/**
 * Process-shared Reader REMOTE lane and HALF_OPEN lease owner.
 *
 * The lane is keyed by source ID (not source object identity), so plugin-registry recreation cannot
 * accidentally admit concurrent Reader requests for the same source.
 */
class ReaderSourceExecutionLimiter {
    private data class Waiter(
        val ownerJob: Job,
        val workJob: Job,
        val priority: ReaderRemoteWorkPriority,
        val ready: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private data class Active(
        val workJob: Job,
        val priority: ReaderRemoteWorkPriority,
    )

    private class Lane {
        var active: Active? = null
        val foreground = ArrayDeque<Waiter>()
        val prefetch = ArrayDeque<Waiter>()
    }

    private val laneLock = Any()
    private val lanes = mutableMapOf<PluginId, Lane>()
    private val probeLock = Any()
    private val heldProbes = mutableSetOf<SourceOperationKey>()
    private val remoteForegroundPermit = Semaphore(
        permits = ReaderRuntimeLimits.MAX_CONCURRENT_FOREGROUND_REMOTE,
    )
    private val remotePrefetchPermit = Semaphore(
        permits = ReaderRuntimeLimits.MAX_CONCURRENT_PREFETCH_REMOTE,
    )

    internal fun tryAcquireHalfOpenProbe(key: SourceOperationKey): ReaderHalfOpenProbeLease? =
        synchronized(probeLock) {
            if (!heldProbes.add(key)) return@synchronized null
            ReaderHalfOpenProbeLease(key, ::releaseProbe)
        }

    internal suspend fun <T> withRemotePermit(
        sourceId: PluginId,
        priority: ReaderRemoteWorkPriority,
        block: suspend () -> T,
    ): T = withSourceRemotePermit(sourceId, priority) {
        when (priority) {
            ReaderRemoteWorkPriority.FOREGROUND -> remoteForegroundPermit.withPermit { block() }
            ReaderRemoteWorkPriority.PREFETCH -> remotePrefetchPermit.withPermit { block() }
        }
    }

    private suspend fun <T> withSourceRemotePermit(
        sourceId: PluginId,
        priority: ReaderRemoteWorkPriority,
        block: suspend () -> T,
    ): T {
        val ownerJob = checkNotNull(currentCoroutineContext()[Job]) {
            "Reader remote execution requires a coroutine Job."
        }
        // Preemption cancels Reader-owned work, never the caller/session Job itself. This lets a
        // caller observe ReaderPrefetchPreemptedException without poisoning its parent scope.
        val workJob = Job(ownerJob)
        val waiter = Waiter(ownerJob, workJob, priority)
        var prefetchToPreempt: Job? = null
        synchronized<Unit>(laneLock) {
            val lane = lanes.getOrPut(sourceId, ::Lane)
            if (lane.active == null && lane.foreground.isEmpty() && lane.prefetch.isEmpty()) {
                lane.active = Active(workJob, priority)
                waiter.ready.complete(Unit)
            } else {
                queue(lane, waiter)
                if (priority == ReaderRemoteWorkPriority.FOREGROUND) {
                    lane.active
                        ?.takeIf { it.priority == ReaderRemoteWorkPriority.PREFETCH }
                        ?.let { prefetchToPreempt = it.workJob }
                }
            }
        }
        prefetchToPreempt?.cancel(ReaderPrefetchPreemptedException())

        var acquired = false
        try {
            waiter.ready.await()
            acquired = true
            return withContext(workJob) { block() }
        } catch (cancelled: CancellationException) {
            if (!acquired) removeQueuedOrGranted(sourceId, waiter)
            throw cancelled
        } finally {
            if (acquired) releaseActive(sourceId, workJob)
            if (workJob.isActive) workJob.cancel()
        }
    }

    private fun queue(lane: Lane, waiter: Waiter) {
        when (waiter.priority) {
            ReaderRemoteWorkPriority.FOREGROUND -> lane.foreground.addLast(waiter)
            ReaderRemoteWorkPriority.PREFETCH -> lane.prefetch.addLast(waiter)
        }
    }

    private fun removeQueuedOrGranted(sourceId: PluginId, waiter: Waiter) {
        val toResume = mutableListOf<Waiter>()
        synchronized(laneLock) {
            val lane = lanes[sourceId] ?: return
            lane.foreground.remove(waiter)
            lane.prefetch.remove(waiter)
            if (lane.active?.workJob == waiter.workJob) {
                lane.active = null
                grantNextLocked(lane)?.let(toResume::add)
            }
            pruneLaneLocked(sourceId, lane)
        }
        toResume.forEach { it.ready.complete(Unit) }
    }

    private fun releaseActive(sourceId: PluginId, workJob: Job) {
        val next = synchronized(laneLock) {
            val lane = lanes[sourceId] ?: return
            if (lane.active?.workJob != workJob) return
            lane.active = null
            grantNextLocked(lane).also { pruneLaneLocked(sourceId, lane) }
        }
        next?.ready?.complete(Unit)
    }

    private fun grantNextLocked(lane: Lane): Waiter? {
        while (true) {
            val next = when {
                lane.foreground.isNotEmpty() -> lane.foreground.removeFirst()
                lane.prefetch.isNotEmpty() -> lane.prefetch.removeFirst()
                else -> return null
            }
            if (!next.ownerJob.isActive || !next.workJob.isActive) continue
            lane.active = Active(next.workJob, next.priority)
            return next
        }
    }

    private fun pruneLaneLocked(sourceId: PluginId, lane: Lane) {
        if (lane.active == null && lane.foreground.isEmpty() && lane.prefetch.isEmpty()) {
            lanes.remove(sourceId)
        }
    }

    private fun releaseProbe(key: SourceOperationKey) {
        synchronized(probeLock) {
            heldProbes.remove(key)
        }
    }
}
