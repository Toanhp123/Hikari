package app.openstory.common

fun interface Clock {
    fun nowEpochMillis(): Long
}

object SystemClock : Clock {
    override fun nowEpochMillis(): Long =
        System.currentTimeMillis()
}

fun interface MonotonicClock {
    fun nowNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long =
        System.nanoTime()
}

class FakeClock(
    initialEpochMillis: Long,
) : Clock {
    private var currentEpochMillis = initialEpochMillis

    override fun nowEpochMillis(): Long =
        currentEpochMillis

    fun advanceBy(durationMillis: Long) {
        currentEpochMillis += durationMillis
    }
}

class FakeMonotonicClock(
    initialNanos: Long,
) : MonotonicClock {
    private var currentNanos = initialNanos

    override fun nowNanos(): Long =
        currentNanos

    fun advanceByNanos(durationNanos: Long) {
        require(durationNanos >= 0L) { "Monotonic duration must not be negative" }
        currentNanos += durationNanos
    }
}
