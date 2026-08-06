package app.openstory.common

fun interface Clock {
    fun nowEpochMillis(): Long
}

object SystemClock : Clock {
    override fun nowEpochMillis(): Long =
        System.currentTimeMillis()
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
