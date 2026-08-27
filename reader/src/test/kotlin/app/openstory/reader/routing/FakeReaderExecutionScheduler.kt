package app.openstory.reader.routing

import kotlinx.coroutines.test.TestCoroutineScheduler

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class FakeReaderExecutionScheduler(
    private val testScheduler: TestCoroutineScheduler,
) : ReaderExecutionScheduler {
    override suspend fun delayMillis(durationMillis: Long) {
        kotlinx.coroutines.delay(durationMillis)
    }

    override fun monotonicNanos(): Long = testScheduler.currentTime * NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
