package app.openstory.reader.routing

import app.openstory.reader.engine.SourceOperationKey
import java.util.concurrent.atomic.AtomicBoolean

internal class ReaderHalfOpenProbeLease(
    internal val key: SourceOperationKey,
    private val onRelease: (SourceOperationKey) -> Unit,
) {
    private val released = AtomicBoolean(false)

    fun release() {
        if (released.compareAndSet(false, true)) onRelease(key)
    }
}

class ReaderHalfOpenProbeRegistry {
    private val lock = Any()
    private val held = mutableSetOf<SourceOperationKey>()

    internal fun tryAcquire(key: SourceOperationKey): ReaderHalfOpenProbeLease? =
        synchronized(lock) {
            if (!held.add(key)) return@synchronized null
            ReaderHalfOpenProbeLease(key, ::release)
        }

    private fun release(key: SourceOperationKey) {
        synchronized(lock) { held.remove(key) }
    }
}
