package app.openstory.reader.routing

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

interface ReaderExecutionScheduler {
    suspend fun delayMillis(durationMillis: Long)

    fun monotonicNanos(): Long
}

class DefaultReaderExecutionScheduler private constructor(
    private val delayBlock: suspend (Long) -> Unit,
    private val rawMonotonicNanos: () -> Long,
) : ReaderExecutionScheduler {
    constructor() : this(delayBlock = { delay(it) }, rawMonotonicNanos = System::nanoTime)

    private val lastCompletionNanos = AtomicLong(-1L)

    override suspend fun delayMillis(durationMillis: Long) {
        require(durationMillis >= 0L) { "Reader execution delay must be non-negative." }
        delayBlock(durationMillis)
    }

    override fun monotonicNanos(): Long {
        val raw = rawMonotonicNanos()
        require(raw >= 0L) { "Reader monotonic time must be non-negative." }
        while (true) {
            val previous = lastCompletionNanos.get()
            val next = maxOf(raw, previous + 1L)
            if (lastCompletionNanos.compareAndSet(previous, next)) return next
        }
    }

    companion object {
        internal fun forTest(
            delayBlock: suspend (Long) -> Unit,
            rawMonotonicNanos: () -> Long,
        ): DefaultReaderExecutionScheduler = DefaultReaderExecutionScheduler(
            delayBlock = delayBlock,
            rawMonotonicNanos = rawMonotonicNanos,
        )
    }
}
