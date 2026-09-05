package app.openstory.reader.assets

import app.openstory.common.MonotonicClock
import app.openstory.common.SystemMonotonicClock
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

enum class ContentFetchPriority {
    CRITICAL,
    INTERACTIVE,
    USER_WORK,
    PREFETCH,
    SPECULATIVE,
    BACKGROUND,
}

class ContentFetchDemand internal constructor(initial: ContentFetchPriority) {
    private val lock = Any()
    private var currentPriority = initial
    private val listeners = mutableMapOf<Long, () -> Unit>()

    val priority: ContentFetchPriority
        get() = synchronized(lock) { currentPriority }

    fun promoteTo(priority: ContentFetchPriority) {
        val callbacks = synchronized(lock) {
            if (priority.precedence >= currentPriority.precedence) return
            currentPriority = priority
            listeners.values.toList()
        }
        callbacks.forEach { it() }
    }

    internal fun addListener(listener: () -> Unit): Long = synchronized(lock) {
        val id = nextListenerId.incrementAndGet()
        listeners[id] = listener
        id
    }

    internal fun removeListener(id: Long) {
        synchronized(lock) { listeners.remove(id) }
    }

    private companion object {
        val nextListenerId = AtomicLong()
    }
}

class ContentFetchArbiter(
    private val maxTotal: Int = ReaderAssetRuntimePolicy.MAX_TOTAL_CONTENT_FETCHES,
    private val reservedCriticalInteractive: Int =
        ReaderAssetRuntimePolicy.RESERVED_CRITICAL_INTERACTIVE_SLOTS,
    private val maxSpeculative: Int =
        ReaderAssetRuntimePolicy.MAX_NEXT_CHAPTER_SPECULATIVE_FETCHES,
    private val userWorkAgingThresholdNanos: Long = USER_WORK_AGING_THRESHOLD_NANOS,
    private val monotonicClock: MonotonicClock = SystemMonotonicClock,
) {
    private data class Waiter(
        val sequence: Long,
        val enqueuedAtNanos: Long,
        val demand: ContentFetchDemand,
        val ready: CompletableDeferred<Unit> = CompletableDeferred(),
        var listenerId: Long = 0L,
    )

    private val lock = Any()
    private val queued = mutableListOf<Waiter>()
    private val active = mutableSetOf<Waiter>()
    private var nextSequence = 0L

    init {
        require(maxTotal > 0)
        require(reservedCriticalInteractive in 0..maxTotal)
        require(maxSpeculative in 0..maxTotal)
        require(userWorkAgingThresholdNanos >= 0L)
    }

    fun newDemand(priority: ContentFetchPriority): ContentFetchDemand =
        ContentFetchDemand(priority)

    suspend fun <T> withAdmission(
        demand: ContentFetchDemand,
        block: suspend () -> T,
    ): T {
        check(currentCoroutineContext()[ContentFetchAdmissionMarker] == null) {
            "Content fetch admission cannot be nested."
        }
        val waiter = synchronized(lock) {
            Waiter(
                sequence = nextSequence++,
                enqueuedAtNanos = monotonicClock.nowNanos(),
                demand = demand,
            ).also { created ->
                created.listenerId = demand.addListener(::onQueueChanged)
                queued += created
            }
        }
        resumeEligible()

        var acquired = false
        try {
            waiter.ready.await()
            acquired = true
            return withContext(ContentFetchAdmissionMarker()) { block() }
        } finally {
            demand.removeListener(waiter.listenerId)
            if (acquired) release(waiter) else cancel(waiter)
        }
    }

    suspend fun <T> withAdmission(
        priority: ContentFetchPriority,
        block: suspend () -> T,
    ): T = withAdmission(newDemand(priority), block)

    private fun onQueueChanged() {
        resumeEligible()
    }

    private fun cancel(waiter: Waiter) {
        val ready = synchronized(lock) {
            queued.remove(waiter)
            active.remove(waiter)
            drainLocked()
        }
        ready.forEach { it.ready.complete(Unit) }
    }

    private fun release(waiter: Waiter) {
        val ready = synchronized(lock) {
            active.remove(waiter)
            drainLocked()
        }
        ready.forEach { it.ready.complete(Unit) }
    }

    private fun resumeEligible() {
        val ready = synchronized(lock) { drainLocked() }
        ready.forEach { it.ready.complete(Unit) }
    }

    private fun drainLocked(): List<Waiter> {
        val ready = mutableListOf<Waiter>()
        var usedAgedOverride = false
        while (active.size < maxTotal) {
            val selection = selectNextLocked(usedAgedOverride) ?: break
            val next = selection.waiter
            usedAgedOverride = usedAgedOverride || selection.usedAgedOverride
            queued.remove(next)
            active += next
            ready += next
        }
        return ready
    }

    private fun canAdmitLocked(waiter: Waiter): Boolean {
        val priority = waiter.demand.priority
        val speculativeCapacityAvailable = if (priority == ContentFetchPriority.SPECULATIVE) {
            val activeSpeculative = active.count {
                it.demand.priority == ContentFetchPriority.SPECULATIVE
            }
            activeSpeculative < maxSpeculative
        } else {
            true
        }
        val priorityCapacityAvailable = if (!priority.isVisible) {
            val activeNonVisible = active.count { !it.demand.priority.isVisible }
            activeNonVisible < maxTotal - reservedCriticalInteractive
        } else {
            true
        }
        return speculativeCapacityAvailable && priorityCapacityAvailable
    }

    private fun selectNextLocked(usedAgedOverride: Boolean): Selection? {
        val eligible = queued.filter(::canAdmitLocked)
        val normal = eligible.minWithOrNull(
            compareBy<Waiter>({ it.demand.priority.precedence }, Waiter::sequence),
        ) ?: return null
        val aged = if (usedAgedOverride) null else eligible
            .asSequence()
            .filter { it.demand.priority == ContentFetchPriority.USER_WORK }
            .filter { monotonicClock.nowNanos() - it.enqueuedAtNanos >= userWorkAgingThresholdNanos }
            .minByOrNull(Waiter::sequence)
        return if (aged != null && aged.sequence < normal.sequence) {
            Selection(aged, usedAgedOverride = true)
        } else {
            Selection(normal, usedAgedOverride = false)
        }
    }

    private data class Selection(
        val waiter: Waiter,
        val usedAgedOverride: Boolean,
    )

    private companion object {
        const val USER_WORK_AGING_THRESHOLD_NANOS = 2_000_000_000L
    }
}

private class ContentFetchAdmissionMarker :
    AbstractCoroutineContextElement(ContentFetchAdmissionMarker) {
    companion object : CoroutineContext.Key<ContentFetchAdmissionMarker>
}

private val ContentFetchPriority.precedence: Int
    get() = when (this) {
        ContentFetchPriority.CRITICAL -> 0
        ContentFetchPriority.INTERACTIVE -> 1
        ContentFetchPriority.USER_WORK -> 2
        ContentFetchPriority.PREFETCH -> 3
        ContentFetchPriority.SPECULATIVE -> 4
        ContentFetchPriority.BACKGROUND -> 5
    }

private val ContentFetchPriority.isVisible: Boolean
    get() = this == ContentFetchPriority.CRITICAL || this == ContentFetchPriority.INTERACTIVE
