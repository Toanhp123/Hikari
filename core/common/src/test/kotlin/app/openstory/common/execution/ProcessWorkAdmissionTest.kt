package app.openstory.common.execution

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessWorkAdmissionTest {
    @Test
    fun overflowIsRejectedWhenActiveAndPendingCapacityAreFull() = runTest {
        val admission = BoundedProcessWorkAdmission(
            limitOverrides = mapOf(
                WorkResource.NETWORK to WorkAdmissionLimits(
                    maxActive = 1,
                    maxPending = 1,
                    reservedForegroundActive = 0,
                ),
            ),
        )
        val activeStarted = CompletableDeferred<Unit>()
        val releaseActive = CompletableDeferred<Unit>()
        val active = async {
            admission.run(WorkResource.NETWORK, WorkPriority.VISIBLE_ARTWORK) {
                activeStarted.complete(Unit)
                releaseActive.await()
                "active"
            }
        }
        activeStarted.await()
        val pendingStarted = CompletableDeferred<Unit>()
        val pending = async {
            admission.run(WorkResource.NETWORK, WorkPriority.VISIBLE_ARTWORK) {
                pendingStarted.complete(Unit)
                "pending"
            }
        }
        runCurrent()

        val overflow = admission.run(WorkResource.NETWORK, WorkPriority.VISIBLE_ARTWORK) {
            "overflow"
        }

        assertEquals(
            WorkAdmissionResult.Rejected(WorkRejectionReason.SATURATED),
            overflow,
        )
        assertFalse(pendingStarted.isCompleted)
        releaseActive.complete(Unit)
        assertEquals(WorkAdmissionResult.Completed("active"), active.await())
        assertEquals(WorkAdmissionResult.Completed("pending"), pending.await())
    }

    @Test
    fun foregroundUsesReservedCapacityWhileNoncriticalWorkIsSaturated() = runTest {
        val admission = BoundedProcessWorkAdmission(
            limitOverrides = mapOf(
                WorkResource.DECODE to WorkAdmissionLimits(
                    maxActive = 2,
                    maxPending = 1,
                    reservedForegroundActive = 1,
                ),
            ),
        )
        val releaseNoncritical = CompletableDeferred<Unit>()
        val noncriticalStarted = CompletableDeferred<Unit>()
        val noncritical = async {
            admission.run(WorkResource.DECODE, WorkPriority.NONCRITICAL) {
                noncriticalStarted.complete(Unit)
                releaseNoncritical.await()
            }
        }
        noncriticalStarted.await()
        val queuedNoncriticalStarted = CompletableDeferred<Unit>()
        val queuedNoncritical = async {
            admission.run(WorkResource.DECODE, WorkPriority.NONCRITICAL) {
                queuedNoncriticalStarted.complete(Unit)
            }
        }
        runCurrent()
        val foregroundStarted = CompletableDeferred<Unit>()

        val foreground = async {
            admission.run(WorkResource.DECODE, WorkPriority.FOREGROUND_COMMAND) {
                foregroundStarted.complete(Unit)
                "foreground"
            }
        }
        foregroundStarted.await()

        assertEquals(WorkAdmissionResult.Completed("foreground"), foreground.await())
        assertFalse(queuedNoncriticalStarted.isCompleted)
        releaseNoncritical.complete(Unit)
        noncritical.await()
        queuedNoncritical.await()
    }

    @Test
    fun cancellingQueuedWorkRemovesItAndDoesNotStrandCapacity() = runTest {
        val admission = BoundedProcessWorkAdmission(
            limitOverrides = mapOf(
                WorkResource.NETWORK to WorkAdmissionLimits(
                    maxActive = 1,
                    maxPending = 1,
                    reservedForegroundActive = 0,
                ),
            ),
        )
        val activeStarted = CompletableDeferred<Unit>()
        val active = async {
            admission.run(WorkResource.NETWORK, WorkPriority.FOREGROUND_COMMAND) {
                activeStarted.complete(Unit)
                awaitCancellation()
            }
        }
        activeStarted.await()
        val cancelledRan = CompletableDeferred<Unit>()
        val cancelled = async {
            admission.run(WorkResource.NETWORK, WorkPriority.FOREGROUND_COMMAND) {
                cancelledRan.complete(Unit)
            }
        }
        runCurrent()
        cancelled.cancelAndJoin()

        val replacementStarted = CompletableDeferred<Unit>()
        val replacement = async {
            admission.run(WorkResource.NETWORK, WorkPriority.FOREGROUND_COMMAND) {
                replacementStarted.complete(Unit)
                "replacement"
            }
        }
        runCurrent()
        assertFalse(replacementStarted.isCompleted)

        active.cancelAndJoin()
        assertEquals(WorkAdmissionResult.Completed("replacement"), replacement.await())
        assertTrue(replacementStarted.isCompleted)
        assertFalse(cancelledRan.isCompleted)
    }

    @Test
    fun queuedForegroundRunsBeforeEarlierNoncriticalWork() = runTest {
        val admission = BoundedProcessWorkAdmission(
            limitOverrides = mapOf(
                WorkResource.NETWORK to WorkAdmissionLimits(
                    maxActive = 1,
                    maxPending = 2,
                    reservedForegroundActive = 0,
                ),
            ),
        )
        val releaseActive = CompletableDeferred<Unit>()
        val activeStarted = CompletableDeferred<Unit>()
        val active = async {
            admission.run(WorkResource.NETWORK, WorkPriority.VISIBLE_ARTWORK) {
                activeStarted.complete(Unit)
                releaseActive.await()
            }
        }
        activeStarted.await()
        val noncriticalStarted = CompletableDeferred<Unit>()
        val noncritical = async {
            admission.run(WorkResource.NETWORK, WorkPriority.NONCRITICAL) {
                noncriticalStarted.complete(Unit)
            }
        }
        runCurrent()
        val foregroundStarted = CompletableDeferred<Unit>()
        val foreground = async {
            admission.run(WorkResource.NETWORK, WorkPriority.FOREGROUND_COMMAND) {
                foregroundStarted.complete(Unit)
            }
        }
        runCurrent()

        releaseActive.complete(Unit)
        foregroundStarted.await()

        assertFalse(noncriticalStarted.isCompleted)
        active.await()
        foreground.await()
        noncritical.await()
    }

    @Test
    fun blockFailurePropagatesAndReleasesCapacity() = runTest {
        val admission = BoundedProcessWorkAdmission(
            limitOverrides = mapOf(
                WorkResource.DECODE to WorkAdmissionLimits(
                    maxActive = 1,
                    maxPending = 1,
                    reservedForegroundActive = 0,
                ),
            ),
        )

        assertFailsWith<IllegalStateException> {
            admission.run(WorkResource.DECODE, WorkPriority.FOREGROUND_COMMAND) {
                error("decode failed")
            }
        }
        assertEquals(
            WorkAdmissionResult.Completed("recovered"),
            admission.run(WorkResource.DECODE, WorkPriority.FOREGROUND_COMMAND) {
                "recovered"
            },
        )
    }
}
