package app.openstory.catalog.feature.assets

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverJobLimiterTest {
    @Test
    fun ninthDemandWaitsAndCancellationPreventsItFromStarting() = runTest {
        val limiter = CoverJobLimiter(maxActiveJobs = 8)
        val release = CompletableDeferred<Unit>()
        val entered = AtomicInteger()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val jobs = List(9) {
            async {
                limiter.withPermit {
                    entered.incrementAndGet()
                    val current = active.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, current) }
                    release.await()
                    active.decrementAndGet()
                }
            }
        }

        while (entered.get() < 8) yield()
        assertEquals(8, entered.get())
        assertEquals(8, peak.get())

        jobs.last().cancelAndJoin()
        release.complete(Unit)
        jobs.dropLast(1).awaitAll()

        assertEquals(8, entered.get())
        assertEquals(0, active.get())
    }
}
