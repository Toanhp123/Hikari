package app.openstory.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClockTest {
    @Test
    fun wallAndMonotonicClocksAdvanceIndependently() {
        val wall = FakeClock(1_000L)
        val monotonic = FakeMonotonicClock(5_000L)

        wall.advanceBy(250L)
        assertEquals(1_250L, wall.nowEpochMillis())
        assertEquals(5_000L, monotonic.nowNanos())

        monotonic.advanceByNanos(750L)
        assertEquals(1_250L, wall.nowEpochMillis())
        assertEquals(5_750L, monotonic.nowNanos())
    }

    @Test
    fun monotonicClockRejectsBackwardMovement() {
        val clock = FakeMonotonicClock(10L)

        assertFailsWith<IllegalArgumentException> { clock.advanceByNanos(-1L) }
    }

    @Test
    fun systemMonotonicClockDoesNotMoveBackward() {
        val first = SystemMonotonicClock.nowNanos()
        val second = SystemMonotonicClock.nowNanos()

        assertTrue(second >= first)
    }
}
