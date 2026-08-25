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
        repeat(3) { index ->
            registry.record(
                key,
                SourceObservation.TransportFailure.Timeout(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT),
                index.toLong(),
            )
        }
        assertEquals(CircuitState.OPEN, registry.snapshot(key, 29_999L).state.circuitState)
        assertEquals(CircuitState.HALF_OPEN, registry.snapshot(key, 30_002L).state.circuitState)
    }
}
