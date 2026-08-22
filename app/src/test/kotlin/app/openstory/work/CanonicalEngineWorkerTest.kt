package app.openstory.work

import app.openstory.catalog.orchestration.CanonicalMaintenanceReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class CanonicalEngineWorkerTest {
    @Test
    fun persistedItemRetryDoesNotUseWorkManagerRetryClock() = runTest {
        val decision = runCanonicalEngineWork {
            CanonicalMaintenanceReport(
                processed = 1,
                succeeded = 0,
                retried = 1,
                failedInvariant = 0,
                nextAttemptAtEpochMillis = 10_000L,
            )
        }

        assertEquals(CanonicalWorkRunDecision.SUCCESS, decision)
    }

    @Test
    fun invariantFailureIsDurablyParkedWithoutPoisoningWorkManagerChain() = runTest {
        val decision = runCanonicalEngineWork {
            CanonicalMaintenanceReport(
                processed = 1,
                succeeded = 0,
                retried = 0,
                failedInvariant = 1,
                nextAttemptAtEpochMillis = null,
            )
        }

        assertEquals(CanonicalWorkRunDecision.SUCCESS, decision)
    }

    @Test
    fun periodicSafetyWorkerUsesSuccessSoInvariantReportsDoNotCancelTheSchedule() {
        assertEquals(CanonicalWorkRunDecision.SUCCESS, decideCanonicalSafetyWorkerResult())
    }

    @Test
    fun unexpectedDrainFailureUsesWorkManagerRetryAsOuterWakeupOnly() = runTest {
        val decision = runCanonicalEngineWork { error("database temporarily unavailable") }

        assertEquals(CanonicalWorkRunDecision.RETRY, decision)
    }

    @Test
    fun retryWakeDelayNeverRunsBeforeDurableQueueTimeAndAvoidsImmediateOverlap() {
        assertEquals(10_000L, canonicalRetryWakeDelayMillis(nowEpochMillis = 1_000L, nextAttemptAtEpochMillis = 2_000L))
        assertEquals(
            20_000L,
            canonicalRetryWakeDelayMillis(nowEpochMillis = 1_000L, nextAttemptAtEpochMillis = 21_000L),
        )
    }

    @Test
    fun retryWakeUsesDurableTimestampInUniqueNameSoLaterWakeCannotReplaceEarlierWake() {
        assertEquals("canonical-engine-retry-wake:2000", canonicalRetryWakeWorkName(2_000L))
        assertEquals("canonical-engine-retry-wake:21000", canonicalRetryWakeWorkName(21_000L))
    }
    @Test
    fun readyBacklogSchedulesAnotherDrainWhileFutureBacklogSchedulesDurableWake() {
        val now = 1_000L
        assertEquals(
            CanonicalWorkerWake.DRAIN,
            decideCanonicalWorkerWake(now, nextAttemptAtEpochMillis = now),
        )
        assertEquals(
            CanonicalWorkerWake.RETRY_WAKE,
            decideCanonicalWorkerWake(now, nextAttemptAtEpochMillis = now + 60_000L),
        )
        assertEquals(
            CanonicalWorkerWake.NONE,
            decideCanonicalWorkerWake(now, nextAttemptAtEpochMillis = null),
        )
    }

}
