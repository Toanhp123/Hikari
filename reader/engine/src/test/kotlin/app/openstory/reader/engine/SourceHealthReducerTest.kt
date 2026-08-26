package app.openstory.reader.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SourceHealthReducerTest {
    private val reducer = SourceHealthReducer.v1()
    private val policy = HealthPolicy.v1()

    @Test
    fun thirdDefaultPenalizingFailureOpensNeutralCircuit() {
        var state = SourceHealthState()
        state = reducer.reduce(state, timeout(), 1_000L, policy)
        assertEquals(CircuitState.CLOSED, state.circuitState)
        assertEquals(BasisPoints(8_000), state.successEwmaBasisPoints)
        state = reducer.reduce(state, timeout(), 2_000L, policy)
        assertEquals(CircuitState.CLOSED, state.circuitState)
        assertEquals(BasisPoints(6_400), state.successEwmaBasisPoints)
        state = reducer.reduce(state, timeout(), 3_000L, policy)

        assertEquals(CircuitState.OPEN, state.circuitState)
        assertEquals(BasisPoints(5_120), state.successEwmaBasisPoints)
        assertEquals(3, state.consecutivePenalizingFailures)
        assertEquals(1, state.openCount)
        assertEquals(3_000L, state.openedAtEpochMillis)
        assertEquals(33_000L, state.nextProbeAtEpochMillis)
    }

    @Test
    fun advanceMovesOpenToHalfOpenAtCooldownBoundary() {
        val opened = openedState(at = 10_000L)

        assertEquals(CircuitState.OPEN, reducer.advance(opened, 39_999L, policy).circuitState)
        val halfOpen = reducer.advance(opened, 40_000L, policy)
        assertEquals(CircuitState.HALF_OPEN, halfOpen.circuitState)
        assertEquals(opened.openCount, halfOpen.openCount)
        assertEquals(opened.nextProbeAtEpochMillis, halfOpen.nextProbeAtEpochMillis)
    }

    @Test
    fun successfulHalfOpenProbeClosesAndResetsOpenCount() {
        val halfOpen = reducer.advance(openedState(at = 1_000L), 31_000L, policy)
        val closed = reducer.reduce(
            halfOpen,
            SourceObservation.Success.Remote(RemoteAttemptKind.HALF_OPEN_PROBE, 125L),
            31_100L,
            policy,
        )

        assertEquals(CircuitState.CLOSED, closed.circuitState)
        assertEquals(0, closed.consecutivePenalizingFailures)
        assertEquals(0, closed.openCount)
        assertNull(closed.openedAtEpochMillis)
        assertNull(closed.nextProbeAtEpochMillis)
        assertEquals(listOf(125L), closed.recentLatencySamplesMillis)
    }

    @Test
    fun failedHalfOpenProbeReopensWithExponentialCooldownCappedAtFiveMinutes() {
        var state = SourceHealthState(
            circuitState = CircuitState.HALF_OPEN,
            consecutivePenalizingFailures = 3,
            successEwmaBasisPoints = BasisPoints(5_120),
            openCount = 1,
            openedAtEpochMillis = 1_000L,
            nextProbeAtEpochMillis = 31_000L,
        )
        state = reducer.reduce(
            state,
            SourceObservation.TransportFailure.Connection(RemoteAttemptKind.HALF_OPEN_PROBE),
            31_100L,
            policy,
        )
        assertEquals(CircuitState.OPEN, state.circuitState)
        assertEquals(2, state.openCount)
        assertEquals(91_100L, state.nextProbeAtEpochMillis)

        repeat(10) { index ->
            state = reducer.advance(state, checkNotNull(state.nextProbeAtEpochMillis), policy)
            state = reducer.reduce(
                state,
                SourceObservation.TransportFailure.Connection(RemoteAttemptKind.HALF_OPEN_PROBE),
                checkNotNull(state.nextProbeAtEpochMillis) + index + 1L,
                policy,
            )
        }
        assertEquals(300_000L, checkNotNull(state.nextProbeAtEpochMillis) - checkNotNull(state.openedAtEpochMillis))
    }

    @Test
    fun lateNormalSuccessWhileOpenCannotCloseOrResetOpenCycle() {
        val opened = openedState(at = 2_000L)
        val updated = reducer.reduce(
            opened,
            SourceObservation.Success.Remote(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT, 200L),
            2_100L,
            policy,
        )

        assertEquals(CircuitState.OPEN, updated.circuitState)
        assertEquals(opened.openCount, updated.openCount)
        assertEquals(opened.consecutivePenalizingFailures, updated.consecutivePenalizingFailures)
        assertEquals(opened.openedAtEpochMillis, updated.openedAtEpochMillis)
        assertEquals(opened.nextProbeAtEpochMillis, updated.nextProbeAtEpochMillis)
        assertEquals(listOf(200L), updated.recentLatencySamplesMillis)
        assertEquals(BasisPoints(6_096), updated.successEwmaBasisPoints)
    }

    @Test
    fun lateNormalFailureInHalfOpenCannotClaimProbeAuthority() {
        val halfOpen = reducer.advance(openedState(at = 1_000L), 31_000L, policy)
        val updated = reducer.reduce(
            halfOpen,
            SourceObservation.TransportFailure.Connection(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT),
            31_100L,
            policy,
        )

        assertEquals(CircuitState.HALF_OPEN, updated.circuitState)
        assertEquals(halfOpen.openCount, updated.openCount)
        assertEquals(halfOpen.openedAtEpochMillis, updated.openedAtEpochMillis)
        assertEquals(halfOpen.nextProbeAtEpochMillis, updated.nextProbeAtEpochMillis)
    }

    @Test
    fun nonPenalizingObservationsDoNotLowerReliability() {
        val initial = SourceHealthState()
        val observations = listOf<SourceObservation>(
            SourceObservation.AuthFailure.CredentialsUnavailable,
            SourceObservation.Cancellation.Navigation,
            SourceObservation.Cancellation.HedgeLoser,
            SourceObservation.Cancellation.PrefetchPreempted,
            SourceObservation.Success.Local,
            SourceObservation.ReleaseFailure.NotFound,
            SourceObservation.RuntimeFailure.Unexpected,
        )

        observations.forEach { observation ->
            val reduced = reducer.reduce(initial, observation, 1_000L, policy)
            assertEquals(BasisPoints(10_000), reduced.successEwmaBasisPoints, observation.toString())
            assertEquals(0, reduced.consecutivePenalizingFailures, observation.toString())
        }
    }

    @Test
    fun successfulRemoteSamplesAreBoundedAndUseNearestRankPercentiles() {
        var state = SourceHealthState()
        (1L..25L).forEach { latency ->
            state = reducer.reduce(
                state,
                SourceObservation.Success.Remote(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT, latency),
                latency,
                HealthPolicy.v1(maxLatencySamples = 20),
            )
        }

        assertEquals((6L..25L).toList(), state.recentLatencySamplesMillis)
        assertEquals(15L, state.p50LatencyMillis)
        assertEquals(24L, state.p95LatencyMillis)
        assertNull(SourceHealthState(recentLatencySamplesMillis = listOf(10L, 20L)).p50LatencyMillis)
        assertNull(SourceHealthState(recentLatencySamplesMillis = listOf(10L, 20L)).p95LatencyMillis)
    }

    @Test
    fun nearestRankPercentilesUseExactIntegerRanks() {
        val three = SourceHealthState(recentLatencySamplesMillis = listOf(10L, 20L, 30L))
        assertEquals(20L, three.p50LatencyMillis)
        assertEquals(30L, three.p95LatencyMillis)

        val four = SourceHealthState(recentLatencySamplesMillis = listOf(10L, 20L, 30L, 40L))
        assertEquals(20L, four.p50LatencyMillis)
        assertEquals(40L, four.p95LatencyMillis)

        val twenty = SourceHealthState(recentLatencySamplesMillis = (1L..20L).toList())
        assertEquals(10L, twenty.p50LatencyMillis)
        assertEquals(19L, twenty.p95LatencyMillis)

        val insufficient = SourceHealthState(recentLatencySamplesMillis = listOf(10L, 20L))
        assertNull(insufficient.p50LatencyMillis)
        assertNull(insufficient.p95LatencyMillis)
    }

    @Test
    fun v1HealthPolicyRejectsInvalidBounds() {
        assertFailsWith<IllegalArgumentException> { HealthPolicy.v1(alpha = BasisPoints(0)) }
        assertFailsWith<IllegalArgumentException> { HealthPolicy.v1(openAfterConsecutivePenalizingFailures = 0) }
        assertFailsWith<IllegalArgumentException> { HealthPolicy.v1(openAfterConsecutivePenalizingFailures = 21) }
        assertFailsWith<IllegalArgumentException> { HealthPolicy.v1(minimumCooldownMillis = 0L) }
        assertFailsWith<IllegalArgumentException> {
            HealthPolicy.v1(minimumCooldownMillis = 2L, maximumCooldownMillis = 1L)
        }
        assertFailsWith<IllegalArgumentException> { HealthPolicy.v1(maxLatencySamples = 0) }
        assertFailsWith<IllegalArgumentException> { HealthPolicy.v1(maxLatencySamples = 21) }
    }

    private fun openedState(at: Long): SourceHealthState {
        var state = SourceHealthState()
        repeat(3) { index ->
            state = reducer.reduce(state, timeout(), at - 2L + index, policy)
        }
        return state
    }

    private fun timeout() = SourceObservation.TransportFailure.Timeout(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT)
}
