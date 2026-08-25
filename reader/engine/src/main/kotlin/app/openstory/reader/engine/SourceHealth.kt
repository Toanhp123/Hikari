package app.openstory.reader.engine

import app.openstory.common.id.PluginId

enum class SourceOperation {
    READ_DOCUMENT,
}

data class SourceOperationKey(
    val sourceId: PluginId,
    val operation: SourceOperation = SourceOperation.READ_DOCUMENT,
)

enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN,
}

enum class SourceHealthOrigin {
    STARTUP_NEUTRAL,
    PROCESS_OBSERVED,
}

enum class RemoteAttemptKind {
    NORMAL_REMOTE_ATTEMPT,
    HALF_OPEN_PROBE,
}

@ConsistentCopyVisibility
data class HealthPolicy private constructor(
    val version: HealthPolicyVersion,
    val alpha: BasisPoints,
    val openAfterConsecutivePenalizingFailures: Int,
    val openAtOrBelowReliability: BasisPoints,
    val minimumCooldownMillis: Long,
    val maximumCooldownMillis: Long,
    val maxLatencySamples: Int,
) {
    init {
        require(alpha.value in 1..10_000) { "Health alpha must be in 1..10_000." }
        require(openAfterConsecutivePenalizingFailures > 0) {
            "Health open failure count must be positive."
        }
        require(minimumCooldownMillis > 0L) { "Health minimum cooldown must be positive." }
        require(maximumCooldownMillis >= minimumCooldownMillis) {
            "Health maximum cooldown must be >= minimum cooldown."
        }
        require(maxLatencySamples in 1..20) {
            "Health max latency samples must be in 1..20 for HES-v1."
        }
    }

    companion object {
        fun v1(
            alpha: BasisPoints = BasisPoints(2_000),
            openAfterConsecutivePenalizingFailures: Int = 3,
            openAtOrBelowReliability: BasisPoints = BasisPoints(5_500),
            minimumCooldownMillis: Long = 30_000L,
            maximumCooldownMillis: Long = 300_000L,
            maxLatencySamples: Int = 20,
        ): HealthPolicy {
            return HealthPolicy(
                version = HealthPolicyVersion.HEALTH_POLICY_V1,
                alpha = alpha,
                openAfterConsecutivePenalizingFailures = openAfterConsecutivePenalizingFailures,
                openAtOrBelowReliability = openAtOrBelowReliability,
                minimumCooldownMillis = minimumCooldownMillis,
                maximumCooldownMillis = maximumCooldownMillis,
                maxLatencySamples = maxLatencySamples,
            )
        }
    }
}

class SourceHealthState(
    val circuitState: CircuitState = CircuitState.CLOSED,
    val consecutivePenalizingFailures: Int = 0,
    val successEwmaBasisPoints: BasisPoints = BasisPoints(10_000),
    recentLatencySamplesMillis: List<Long> = emptyList(),
    val openCount: Int = 0,
    val openedAtEpochMillis: Long? = null,
    val nextProbeAtEpochMillis: Long? = null,
) {
    val recentLatencySamplesMillis: List<Long> = recentLatencySamplesMillis.toList()

    init {
        require(consecutivePenalizingFailures >= 0) {
            "consecutivePenalizingFailures must be non-negative."
        }
        require(openCount >= 0) { "openCount must be non-negative." }
        require(this.recentLatencySamplesMillis.size <= 20) {
            "HES-v1 health history must retain at most 20 latency samples."
        }
        require(this.recentLatencySamplesMillis.all { it >= 0L }) {
            "Latency samples must be non-negative."
        }
    }

    fun copy(
        circuitState: CircuitState = this.circuitState,
        consecutivePenalizingFailures: Int = this.consecutivePenalizingFailures,
        successEwmaBasisPoints: BasisPoints = this.successEwmaBasisPoints,
        recentLatencySamplesMillis: List<Long> = this.recentLatencySamplesMillis,
        openCount: Int = this.openCount,
        openedAtEpochMillis: Long? = this.openedAtEpochMillis,
        nextProbeAtEpochMillis: Long? = this.nextProbeAtEpochMillis,
    ): SourceHealthState = SourceHealthState(
        circuitState = circuitState,
        consecutivePenalizingFailures = consecutivePenalizingFailures,
        successEwmaBasisPoints = successEwmaBasisPoints,
        recentLatencySamplesMillis = recentLatencySamplesMillis,
        openCount = openCount,
        openedAtEpochMillis = openedAtEpochMillis,
        nextProbeAtEpochMillis = nextProbeAtEpochMillis,
    )

    override fun equals(other: Any?): Boolean =
        other is SourceHealthState &&
            circuitState == other.circuitState &&
            consecutivePenalizingFailures == other.consecutivePenalizingFailures &&
            successEwmaBasisPoints == other.successEwmaBasisPoints &&
            recentLatencySamplesMillis == other.recentLatencySamplesMillis &&
            openCount == other.openCount &&
            openedAtEpochMillis == other.openedAtEpochMillis &&
            nextProbeAtEpochMillis == other.nextProbeAtEpochMillis

    override fun hashCode(): Int {
        var result = circuitState.hashCode()
        result = 31 * result + consecutivePenalizingFailures
        result = 31 * result + successEwmaBasisPoints.hashCode()
        result = 31 * result + recentLatencySamplesMillis.hashCode()
        result = 31 * result + openCount
        result = 31 * result + (openedAtEpochMillis?.hashCode() ?: 0)
        result = 31 * result + (nextProbeAtEpochMillis?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "SourceHealthState(circuitState=$circuitState, " +
            "consecutivePenalizingFailures=$consecutivePenalizingFailures, " +
            "successEwmaBasisPoints=$successEwmaBasisPoints, " +
            "recentLatencySamplesMillis=$recentLatencySamplesMillis, openCount=$openCount, " +
            "openedAtEpochMillis=$openedAtEpochMillis, nextProbeAtEpochMillis=$nextProbeAtEpochMillis)"
}

data class SourceHealthSnapshot(
    val key: SourceOperationKey,
    val state: SourceHealthState,
    val origin: SourceHealthOrigin,
    val halfOpenProbePermitted: Boolean = false,
) {
    init {
        require(!halfOpenProbePermitted || state.circuitState == CircuitState.HALF_OPEN) {
            "HALF_OPEN probe permission is valid only for a HALF_OPEN source snapshot."
        }
    }
}

sealed interface SourceObservation {
    sealed interface Success : SourceObservation {
        data class Remote(
            val kind: RemoteAttemptKind,
            val latencyMillis: Long,
        ) : Success {
            init {
                require(latencyMillis >= 0L) { "Remote success latency must be non-negative." }
            }
        }

        data object Local : Success
    }

    sealed interface TransportFailure : SourceObservation {
        data object Timeout : TransportFailure
        data object Connection : TransportFailure
        data object RateLimited : TransportFailure
    }

    sealed interface AuthFailure : SourceObservation {
        data object CredentialsUnavailable : AuthFailure
    }

    sealed interface SourceStateFailure : SourceObservation {
        data object DisabledOrNotInstalled : SourceStateFailure
        data object OperationUnavailable : SourceStateFailure
    }

    sealed interface PluginPolicyFailure : SourceObservation {
        data object ConfigurationOrCapability : PluginPolicyFailure
    }

    sealed interface ReleaseFailure : SourceObservation {
        data object NotFound : ReleaseFailure
    }

    sealed interface ContentFailure : SourceObservation {
        data object EmptyDocument : ContentFailure
        data object InvalidDocument : ContentFailure
        data object CorruptDocument : ContentFailure
    }

    sealed interface LocalFailure : SourceObservation {
        data object MissingBlob : LocalFailure
        data object FingerprintOrDecodeMismatch : LocalFailure
    }

    sealed interface Cancellation : SourceObservation {
        data object Navigation : Cancellation
        data object HedgeLoser : Cancellation
        data object PrefetchPreempted : Cancellation
    }

    sealed interface RuntimeFailure : SourceObservation {
        data object Unexpected : RuntimeFailure
    }
}

interface SourceHealthReducer {
    fun advance(
        previous: SourceHealthState,
        nowEpochMillis: Long,
        policy: HealthPolicy,
    ): SourceHealthState

    fun reduce(
        previous: SourceHealthState,
        observation: SourceObservation,
        nowEpochMillis: Long,
        policy: HealthPolicy,
    ): SourceHealthState
}
