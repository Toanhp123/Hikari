package app.openstory.reader.routing

import app.openstory.common.id.PluginId
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

enum class ContentSourceWorkPriority {
    FOREGROUND,
    USER_WORK,
    PREFETCH,
}

internal class ReaderPrefetchPreemptedException : CancellationException(
    "Reader prefetch was preempted by foreground work for the same source.",
)

class ContentSourceExecutionLane {
    private data class Waiter(
        val ownerJob: Job,
        val workJob: Job,
        val priority: ContentSourceWorkPriority,
        val ready: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private data class Active(
        val workJob: Job,
        val priority: ContentSourceWorkPriority,
    )

    private class Lane {
        var active: Active? = null
        val foreground = ArrayDeque<Waiter>()
        val userWork = ArrayDeque<Waiter>()
        val prefetch = ArrayDeque<Waiter>()
    }

    private val lock = Any()
    private val lanes = mutableMapOf<PluginId, Lane>()

    suspend fun <T> withSource(
        sourceId: PluginId,
        priority: ContentSourceWorkPriority,
        block: suspend () -> T,
    ): T {
        val ownerJob = checkNotNull(currentCoroutineContext()[Job]) {
            "Content source execution requires a coroutine Job."
        }
        val workJob = Job(ownerJob)
        val waiter = Waiter(ownerJob, workJob, priority)
        var prefetchToPreempt: Job? = null
        synchronized(lock) {
            val lane = lanes.getOrPut(sourceId, ::Lane)
            if (lane.active == null && lane.isQueueEmpty()) {
                lane.active = Active(workJob, priority)
                waiter.ready.complete(Unit)
            } else {
                lane.enqueue(waiter)
                if (priority == ContentSourceWorkPriority.FOREGROUND) {
                    prefetchToPreempt = lane.active
                        ?.takeIf { it.priority == ContentSourceWorkPriority.PREFETCH }
                        ?.workJob
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

    private fun removeQueuedOrGranted(sourceId: PluginId, waiter: Waiter) {
        val next = synchronized(lock) {
            val lane = lanes[sourceId] ?: return
            lane.remove(waiter)
            if (lane.active?.workJob == waiter.workJob) {
                lane.active = null
                lane.grantNext()
            } else {
                null
            }.also { pruneLane(sourceId, lane) }
        }
        next?.ready?.complete(Unit)
    }

    private fun releaseActive(sourceId: PluginId, workJob: Job) {
        val next = synchronized(lock) {
            val lane = lanes[sourceId] ?: return
            if (lane.active?.workJob != workJob) return
            lane.active = null
            lane.grantNext().also { pruneLane(sourceId, lane) }
        }
        next?.ready?.complete(Unit)
    }

    private fun pruneLane(sourceId: PluginId, lane: Lane) {
        if (lane.active == null && lane.isQueueEmpty()) lanes.remove(sourceId)
    }

    private fun Lane.enqueue(waiter: Waiter) {
        when (waiter.priority) {
            ContentSourceWorkPriority.FOREGROUND -> foreground.addLast(waiter)
            ContentSourceWorkPriority.USER_WORK -> userWork.addLast(waiter)
            ContentSourceWorkPriority.PREFETCH -> prefetch.addLast(waiter)
        }
    }

    private fun Lane.remove(waiter: Waiter) {
        foreground.remove(waiter)
        userWork.remove(waiter)
        prefetch.remove(waiter)
    }

    private fun Lane.grantNext(): Waiter? {
        while (true) {
            val next = when {
                foreground.isNotEmpty() -> foreground.removeFirst()
                userWork.isNotEmpty() -> userWork.removeFirst()
                prefetch.isNotEmpty() -> prefetch.removeFirst()
                else -> return null
            }
            if (!next.ownerJob.isActive || !next.workJob.isActive) continue
            active = Active(next.workJob, next.priority)
            return next
        }
    }

    private fun Lane.isQueueEmpty(): Boolean =
        foreground.isEmpty() && userWork.isEmpty() && prefetch.isEmpty()
}
