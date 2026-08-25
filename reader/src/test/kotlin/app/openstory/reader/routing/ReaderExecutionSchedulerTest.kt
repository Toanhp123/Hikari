package app.openstory.reader.routing

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderExecutionSchedulerTest {
    @Test
    fun `virtual delay completes only at the requested duration`() = runTest {
        val scheduler = FakeReaderExecutionScheduler(testScheduler)
        var completed = false

        launch {
            scheduler.delayMillis(650L)
            completed = true
        }
        runCurrent()
        assertFalse(completed)

        advanceTimeBy(649L)
        runCurrent()
        assertFalse(completed)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(completed)
    }

    @Test
    fun `production completion stamps never move backward or repeat`() {
        val rawValues = ArrayDeque(listOf(10L, 9L, 10L, 15L))
        val scheduler = DefaultReaderExecutionScheduler.forTest(
            delayBlock = {},
            rawMonotonicNanos = rawValues::removeFirst,
        )

        val observed = List(4) { scheduler.monotonicNanos() }

        assertTrue(observed.zipWithNext().all { (previous, next) -> next > previous })
    }
}
