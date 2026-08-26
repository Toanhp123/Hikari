package app.openstory.reader.routing

import app.openstory.common.id.PluginId
import app.openstory.reader.engine.CircuitState
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceHealthOrigin
import app.openstory.reader.engine.SourceHealthState
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSourceHealthRegistryTest {
    private val key = SourceOperationKey(PluginId("source"))

    @Test
    fun observationsRecordedByOneSessionAreVisibleToAnotherSnapshot() = runTest {
        val registry = ReaderSourceHealthRegistry()
        assertEquals(SourceHealthOrigin.STARTUP_NEUTRAL, registry.snapshot(key, 0L).origin)

        registry.record(
            key,
            SourceObservation.TransportFailure.Timeout(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT),
            1L,
        )

        val seenByAnotherSession = registry.snapshot(key, 2L)
        assertEquals(SourceHealthOrigin.PROCESS_OBSERVED, seenByAnotherSession.origin)
        assertEquals(1, seenByAnotherSession.state.consecutivePenalizingFailures)
    }

    @Test
    fun newRegistryStartsNeutralAndHasNoPersistenceDependency() = runTest {
        val snapshot = ReaderSourceHealthRegistry().snapshot(key, 0L)
        assertEquals(SourceHealthOrigin.STARTUP_NEUTRAL, snapshot.origin)
        assertEquals(SourceHealthState(), snapshot.state)
    }

    @Test
    fun snapshotAdvancesOpenCircuitBeforeExposingState() = runTest {
        val registry = ReaderSourceHealthRegistry()
        openCircuit(registry, key)

        assertEquals(CircuitState.OPEN, registry.snapshot(key, 29_999L).state.circuitState)
        assertEquals(CircuitState.HALF_OPEN, registry.snapshot(key, 30_002L).state.circuitState)
    }

    @Test
    fun navigationHedgeAndPrefetchCancellationDoNotPenalizeProcessHealth() = runTest {
        val registry = ReaderSourceHealthRegistry()
        val baseline = registry.snapshot(key, 0L).state

        listOf(
            SourceObservation.Cancellation.Navigation,
            SourceObservation.Cancellation.HedgeLoser,
            SourceObservation.Cancellation.PrefetchPreempted,
        ).forEachIndexed { index, observation ->
            registry.record(key, observation, index.toLong() + 1L)
        }

        val after = registry.snapshot(key, 10L)
        assertEquals(SourceHealthOrigin.PROCESS_OBSERVED, after.origin)
        assertEquals(baseline.circuitState, after.state.circuitState)
        assertEquals(baseline.consecutivePenalizingFailures, after.state.consecutivePenalizingFailures)
        assertEquals(baseline.successEwmaBasisPoints, after.state.successEwmaBasisPoints)
        assertEquals(baseline.openCount, after.state.openCount)
    }

    @Test
    fun lateNormalRemoteSuccessWhileOpenCannotCloseCircuit() = runTest {
        val registry = ReaderSourceHealthRegistry()
        openCircuit(registry, key)
        val before = registry.snapshot(key, 3L).state
        assertEquals(CircuitState.OPEN, before.circuitState)

        registry.record(
            key,
            SourceObservation.Success.Remote(
                kind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
                latencyMillis = 25L,
            ),
            4L,
        )

        val after = registry.snapshot(key, 4L).state
        assertEquals(CircuitState.OPEN, after.circuitState)
        assertEquals(before.openCount, after.openCount)
        assertEquals(before.openedAtEpochMillis, after.openedAtEpochMillis)
        assertEquals(before.nextProbeAtEpochMillis, after.nextProbeAtEpochMillis)
        assertEquals(listOf(25L), after.recentLatencySamplesMillis)
    }

    @Test
    fun halfOpenProbeSuccessClosesWhileProbeFailureReopensWithBackoff() = runTest {
        val successKey = SourceOperationKey(PluginId("success-source"))
        val failureKey = SourceOperationKey(PluginId("failure-source"))
        val registry = ReaderSourceHealthRegistry()
        openCircuit(registry, successKey)
        openCircuit(registry, failureKey)

        assertEquals(CircuitState.HALF_OPEN, registry.snapshot(successKey, 30_002L).state.circuitState)
        registry.record(
            successKey,
            SourceObservation.Success.Remote(
                kind = RemoteAttemptKind.HALF_OPEN_PROBE,
                latencyMillis = 20L,
            ),
            30_003L,
        )
        val closed = registry.snapshot(successKey, 30_003L).state
        assertEquals(CircuitState.CLOSED, closed.circuitState)
        assertEquals(0, closed.openCount)
        assertEquals(0, closed.consecutivePenalizingFailures)

        assertEquals(CircuitState.HALF_OPEN, registry.snapshot(failureKey, 30_002L).state.circuitState)
        registry.record(
            failureKey,
            SourceObservation.TransportFailure.Connection(RemoteAttemptKind.HALF_OPEN_PROBE),
            30_003L,
        )
        val reopened = registry.snapshot(failureKey, 30_003L).state
        assertEquals(CircuitState.OPEN, reopened.circuitState)
        assertEquals(2, reopened.openCount)
        assertEquals(90_003L, reopened.nextProbeAtEpochMillis)
    }

    private suspend fun openCircuit(
        registry: ReaderSourceHealthRegistry,
        operationKey: SourceOperationKey,
    ) {
        repeat(3) { index ->
            registry.record(
                operationKey,
                SourceObservation.TransportFailure.Timeout(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT),
                index.toLong(),
            )
        }
    }
}
