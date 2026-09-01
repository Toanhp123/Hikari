package app.openstory.reader.assets

import app.openstory.common.FakeMonotonicClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class ContentFetchArbiterTest {
    @Test
    fun lowerPriorityWorkPreservesOneVisibleSlot() = runTest {
        val arbiter = arbiter()
        val release = CompletableDeferred<Unit>()
        val entered = Channel<ContentFetchPriority>(Channel.UNLIMITED)
        val jobs = List(3) {
            launch {
                arbiter.withAdmission(ContentFetchPriority.USER_WORK) {
                    entered.send(ContentFetchPriority.USER_WORK)
                    release.await()
                }
            }
        }

        repeat(2) { entered.receive() }
        runCurrent()
        assertFalse(entered.tryReceive().isSuccess)

        val visible = launch {
            arbiter.withAdmission(ContentFetchPriority.CRITICAL) {
                entered.send(ContentFetchPriority.CRITICAL)
                release.await()
            }
        }
        assertEquals(ContentFetchPriority.CRITICAL, entered.receive())

        release.complete(Unit)
        jobs.forEach { it.join() }
        visible.join()
    }

    @Test
    fun speculativeAdmissionIsProcessWideSingleSlot() = runTest {
        val arbiter = arbiter()
        val firstEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        val first = launch {
            arbiter.withAdmission(ContentFetchPriority.SPECULATIVE) {
                active += 1
                maximum = maxOf(maximum, active)
                firstEntered.complete(Unit)
                release.await()
                active -= 1
            }
        }
        firstEntered.await()
        val second = launch {
            arbiter.withAdmission(ContentFetchPriority.SPECULATIVE) {
                active += 1
                maximum = maxOf(maximum, active)
                active -= 1
            }
        }

        runCurrent()
        assertEquals(1, maximum)
        release.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, maximum)
    }

    @Test
    fun promotionReranksTheSameQueuedDemand() = runTest {
        val arbiter = arbiter(maxTotal = 1, reserved = 0)
        val release = CompletableDeferred<Unit>()
        val holderEntered = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val holder = launch {
            arbiter.withAdmission(ContentFetchPriority.CRITICAL) {
                holderEntered.complete(Unit)
                release.await()
            }
        }
        holderEntered.await()
        val demand = arbiter.newDemand(ContentFetchPriority.SPECULATIVE)
        val promoted = launch {
            arbiter.withAdmission(demand) { order += "promoted" }
        }
        val interactive = launch {
            arbiter.withAdmission(ContentFetchPriority.INTERACTIVE) { order += "interactive" }
        }
        runCurrent()

        demand.promoteTo(ContentFetchPriority.CRITICAL)
        release.complete(Unit)
        holder.join()
        promoted.join()
        interactive.join()

        assertEquals(listOf("promoted", "interactive"), order)
    }

    @Test
    fun nestedAdmissionFailsBeforeWaiting() = runTest {
        val arbiter = arbiter()

        arbiter.withAdmission(ContentFetchPriority.CRITICAL) {
            assertFailsWith<IllegalStateException> {
                arbiter.withAdmission(ContentFetchPriority.CRITICAL) { Unit }
            }
        }
    }

    @Test
    fun agedUserWorkClaimsOnlyNonReservedCapacity() = runTest {
        val clock = FakeMonotonicClock(0L)
        val arbiter = ContentFetchArbiter(
            maxTotal = 1,
            reservedCriticalInteractive = 0,
            maxSpeculative = 1,
            userWorkAgingThresholdNanos = 2_000_000_000L,
            monotonicClock = clock,
        )
        val release = CompletableDeferred<Unit>()
        val holderEntered = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val holder = launch {
            arbiter.withAdmission(ContentFetchPriority.CRITICAL) {
                holderEntered.complete(Unit)
                release.await()
            }
        }
        holderEntered.await()
        val userWork = async {
            arbiter.withAdmission(ContentFetchPriority.USER_WORK) { order += "user" }
        }
        runCurrent()
        clock.advanceByNanos(2_000_000_000L)
        val newerCritical = async {
            arbiter.withAdmission(ContentFetchPriority.CRITICAL) { order += "critical" }
        }
        runCurrent()

        release.complete(Unit)
        holder.join()
        userWork.await()
        newerCritical.await()

        assertEquals(listOf("user", "critical"), order)
    }

    private fun arbiter(
        maxTotal: Int = 3,
        reserved: Int = 1,
    ) = ContentFetchArbiter(
        maxTotal = maxTotal,
        reservedCriticalInteractive = reserved,
        maxSpeculative = 1,
        userWorkAgingThresholdNanos = 2_000_000_000L,
        monotonicClock = FakeMonotonicClock(0L),
    )
}
