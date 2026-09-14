package app.openstory.artwork

import app.openstory.common.execution.ProcessWorkAdmission
import app.openstory.common.execution.WorkAdmissionResult
import app.openstory.common.execution.WorkPriority
import app.openstory.common.execution.WorkResource
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

data class ArtworkWorkKey(
    val request: ArtworkRequestIdentity,
    val policy: ArtworkPolicy?,
    val decodeSizeKey: String,
)

internal class ArtworkPipelineCoordinator(
    scope: CoroutineScope,
    private val admission: ProcessWorkAdmission,
    private val onActiveDecodeJobsChanged: (Int) -> Unit = {},
) : AutoCloseable {
    private val inFlight = ArtworkInFlight<ArtworkWorkKey, Any?>(scope)
    private val activeDecodeJobs = AtomicInteger()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> run(key: ArtworkWorkKey, block: suspend () -> T): T =
        inFlight.await(key) {
            val admittedBlock: suspend () -> T = {
                onActiveDecodeJobsChanged(activeDecodeJobs.incrementAndGet())
                try {
                    block()
                } finally {
                    onActiveDecodeJobsChanged(activeDecodeJobs.decrementAndGet())
                }
            }
            when (val result = admission.run(WorkResource.DECODE, WorkPriority.VISIBLE_ARTWORK, admittedBlock)) {
                is WorkAdmissionResult.Completed -> result.value
                is WorkAdmissionResult.Rejected -> throw ArtworkFailureException(ArtworkFailureReason.SATURATED)
            }
        } as T

    override fun close() = inFlight.close()
}

class ArtworkInFlight<K : Any, V>(
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val lock = Any()
    private val entries = mutableMapOf<K, Entry<V>>()
    private val closed = AtomicBoolean(false)

    suspend fun await(key: K, work: suspend () -> V): V {
        check(!closed.get())
        var created = false
        val entry = synchronized(lock) {
            check(!closed.get())
            entries[key]?.also { it.consumers += 1 } ?: Entry(
                deferred = scope.async(start = CoroutineStart.LAZY) { runCatching { work() } },
                consumers = 1,
            ).also {
                entries[key] = it
                created = true
            }
        }
        if (created) entry.deferred.start()
        return try {
            entry.deferred.await().getOrThrow()
        } finally {
            release(key, entry)
        }
    }

    fun activeCount(): Int = synchronized(lock) { entries.size }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val active = synchronized(lock) {
            entries.values.map(Entry<V>::deferred).also { entries.clear() }
        }
        active.forEach { it.cancel() }
    }

    private fun release(key: K, entry: Entry<V>) {
        val cancel = synchronized(lock) {
            if (entries[key] !== entry) return
            entry.consumers -= 1
            if (entry.consumers > 0) return
            entries.remove(key)
            !entry.deferred.isCompleted
        }
        if (cancel) entry.deferred.cancel()
    }

    private data class Entry<V>(
        val deferred: Deferred<Result<V>>,
        var consumers: Int,
    )
}
