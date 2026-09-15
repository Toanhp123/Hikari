package app.openstory.artwork.pipeline

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

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
