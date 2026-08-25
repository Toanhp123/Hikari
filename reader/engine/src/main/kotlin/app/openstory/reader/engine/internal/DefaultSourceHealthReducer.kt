package app.openstory.reader.engine.internal

import app.openstory.reader.engine.BasisPoints
import app.openstory.reader.engine.CircuitState
import app.openstory.reader.engine.HealthPolicy
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceHealthReducer
import app.openstory.reader.engine.SourceHealthState
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.penalizesSourceHealth

internal class DefaultSourceHealthReducer : SourceHealthReducer {
    override fun advance(
        previous: SourceHealthState,
        nowEpochMillis: Long,
        policy: HealthPolicy,
    ): SourceHealthState {
        require(nowEpochMillis >= 0L) { "Health clock must be non-negative." }
        return if (
            previous.circuitState == CircuitState.OPEN &&
            nowEpochMillis >= checkNotNull(previous.nextProbeAtEpochMillis)
        ) {
            previous.copy(circuitState = CircuitState.HALF_OPEN)
        } else {
            previous
        }
    }

    override fun reduce(
        previous: SourceHealthState,
        observation: SourceObservation,
        nowEpochMillis: Long,
        policy: HealthPolicy,
    ): SourceHealthState {
        val advanced = advance(previous, nowEpochMillis, policy)
        return when (observation) {
            is SourceObservation.Success.Remote -> remoteSuccess(advanced, observation, policy)
            SourceObservation.Success.Local -> advanced
            else -> if (observation.penalizesSourceHealth) {
                penalizingFailure(advanced, observation, nowEpochMillis, policy)
            } else {
                advanced
            }
        }
    }

    private fun remoteSuccess(
        previous: SourceHealthState,
        observation: SourceObservation.Success.Remote,
        policy: HealthPolicy,
    ): SourceHealthState {
        val ewma = ewma(previous.successEwmaBasisPoints, 10_000, policy.alpha)
        val latencies = appendLatency(
            previous.recentLatencySamplesMillis,
            observation.latencyMillis,
            policy.maxLatencySamples,
        )
        val hasProbeAuthority = observation.kind == RemoteAttemptKind.HALF_OPEN_PROBE &&
            previous.circuitState == CircuitState.HALF_OPEN
        return if (hasProbeAuthority) {
            SourceHealthState(
                circuitState = CircuitState.CLOSED,
                consecutivePenalizingFailures = 0,
                successEwmaBasisPoints = ewma,
                recentLatencySamplesMillis = latencies,
                openCount = 0,
                openedAtEpochMillis = null,
                nextProbeAtEpochMillis = null,
            )
        } else if (previous.circuitState == CircuitState.CLOSED) {
            previous.copy(
                consecutivePenalizingFailures = 0,
                successEwmaBasisPoints = ewma,
                recentLatencySamplesMillis = latencies,
            )
        } else {
            // A normal attempt may finish after another observation opened the circuit. It may
            // improve bounded quality facts, but never owns OPEN/HALF_OPEN cycle authority.
            previous.copy(
                successEwmaBasisPoints = ewma,
                recentLatencySamplesMillis = latencies,
            )
        }
    }

    private fun penalizingFailure(
        previous: SourceHealthState,
        observation: SourceObservation,
        nowEpochMillis: Long,
        policy: HealthPolicy,
    ): SourceHealthState {
        val ewma = ewma(previous.successEwmaBasisPoints, 0, policy.alpha)
        val consecutive = previous.consecutivePenalizingFailures + 1
        val attemptKind = (observation as? SourceObservation.RemoteAttemptObservation)?.kind

        if (
            previous.circuitState == CircuitState.HALF_OPEN &&
            attemptKind == RemoteAttemptKind.HALF_OPEN_PROBE
        ) {
            return open(
                previous = previous,
                nowEpochMillis = nowEpochMillis,
                policy = policy,
                reliability = ewma,
                consecutiveFailures = consecutive,
                nextOpenCount = previous.openCount + 1,
            )
        }

        if (previous.circuitState != CircuitState.CLOSED) {
            // Late normal failures can update reliability/failure quality but cannot create a new
            // probe cycle or alter the cooldown/openCount that an authoritative failure established.
            return previous.copy(
                consecutivePenalizingFailures = consecutive,
                successEwmaBasisPoints = ewma,
            )
        }

        val shouldOpen = consecutive >= policy.openAfterConsecutivePenalizingFailures &&
            ewma.value <= policy.openAtOrBelowReliability.value
        return if (shouldOpen) {
            open(
                previous = previous,
                nowEpochMillis = nowEpochMillis,
                policy = policy,
                reliability = ewma,
                consecutiveFailures = consecutive,
                nextOpenCount = previous.openCount + 1,
            )
        } else {
            previous.copy(
                consecutivePenalizingFailures = consecutive,
                successEwmaBasisPoints = ewma,
            )
        }
    }

    private fun open(
        previous: SourceHealthState,
        nowEpochMillis: Long,
        policy: HealthPolicy,
        reliability: BasisPoints,
        consecutiveFailures: Int,
        nextOpenCount: Int,
    ): SourceHealthState {
        val cooldown = cooldownMillis(nextOpenCount, policy)
        return previous.copy(
            circuitState = CircuitState.OPEN,
            consecutivePenalizingFailures = consecutiveFailures,
            successEwmaBasisPoints = reliability,
            openCount = nextOpenCount,
            openedAtEpochMillis = nowEpochMillis,
            nextProbeAtEpochMillis = saturatingAdd(nowEpochMillis, cooldown),
        )
    }

    private fun ewma(previous: BasisPoints, sample: Int, alpha: BasisPoints): BasisPoints {
        val weighted = alpha.value.toLong() * sample.toLong() +
            (10_000 - alpha.value).toLong() * previous.value.toLong()
        return BasisPoints((weighted / 10_000L).toInt())
    }

    private fun appendLatency(previous: List<Long>, value: Long, maxSamples: Int): List<Long> =
        (previous + value).takeLast(maxSamples)

    private fun cooldownMillis(openCount: Int, policy: HealthPolicy): Long {
        var cooldown = policy.minimumCooldownMillis
        repeat((openCount - 1).coerceAtLeast(0)) {
            if (cooldown >= policy.maximumCooldownMillis) return policy.maximumCooldownMillis
            cooldown = if (cooldown > Long.MAX_VALUE / 2L) Long.MAX_VALUE else cooldown * 2L
        }
        return cooldown.coerceAtMost(policy.maximumCooldownMillis)
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
