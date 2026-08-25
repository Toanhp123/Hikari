package app.openstory.reader.engine

import app.openstory.common.id.PluginId
import app.openstory.reader.engine.internal.DefaultSourceHealthReducer
import kotlin.math.ceil

const val HES_V1_MAX_HEALTH_FAILURE_THRESHOLD: Int = 20

private const val HES_V1_MAX_LATENCY_SAMPLES: Int = 20
private const val DEFAULT_HEALTH_ALPHA: Int = 2_000
private const val DEFAULT_OPEN_RELIABILITY_THRESHOLD: Int = 5_500
private const val MIN_LATENCY_SAMPLES_FOR_PERCENTILE: Int = 3
private const val PERCENTILE_SCALE: Double = 100.0

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

enum class RecoveryScope {
    RELEASE_SCOPED,
    SOURCE_SCOPED,
    LOCAL_SCOPED,
    CLIENT_SCOPED,
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
        require(alpha.value in 1..BasisPoints.MAX_VALUE) { "Health alpha must be valid positive basis points." }
        require(openAfterConsecutivePenalizingFailures in 1..HES_V1_MAX_HEALTH_FAILURE_THRESHOLD) {
            "Health open failure count must be in 1..$HES_V1_MAX_HEALTH_FAILURE_THRESHOLD for HES-v1."
        }
        require(openAtOrBelowReliability.value in BasisPoints.MIN_VALUE..BasisPoints.MAX_VALUE) {
            "Health open reliability threshold must be valid basis points."
        }
        require(minimumCooldownMillis > 0L) { "Health minimum cooldown must be positive." }
        require(maximumCooldownMillis >= minimumCooldownMillis) {
            "Health maximum cooldown must be >= minimum cooldown."
        }
        require(maxLatencySamples in 1..HES_V1_MAX_LATENCY_SAMPLES) {
            "Health max latency samples must be in 1..$HES_V1_MAX_LATENCY_SAMPLES for HES-v1."
        }
    }

    companion object {
        fun v1(
            alpha: BasisPoints = BasisPoints(DEFAULT_HEALTH_ALPHA),
            openAfterConsecutivePenalizingFailures: Int = 3,
            openAtOrBelowReliability: BasisPoints = BasisPoints(DEFAULT_OPEN_RELIABILITY_THRESHOLD),
            minimumCooldownMillis: Long = 30_000L,
            maximumCooldownMillis: Long = 300_000L,
            maxLatencySamples: Int = HES_V1_MAX_LATENCY_SAMPLES,
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
    val successEwmaBasisPoints: BasisPoints = BasisPoints(BasisPoints.MAX_VALUE),
    recentLatencySamplesMillis: List<Long> = emptyList(),
    val openCount: Int = 0,
    val openedAtEpochMillis: Long? = null,
    val nextProbeAtEpochMillis: Long? = null,
) {
    val recentLatencySamplesMillis: List<Long> = recentLatencySamplesMillis.toList()

    val p50LatencyMillis: Long?
        get() = nearestRankLatency(50)

    val p95LatencyMillis: Long?
        get() = nearestRankLatency(95)

    init {
        require(consecutivePenalizingFailures >= 0) {
            "consecutivePenalizingFailures must be non-negative."
        }
        require(openCount >= 0) { "openCount must be non-negative." }
        require(this.recentLatencySamplesMillis.size <= HES_V1_MAX_LATENCY_SAMPLES) {
            "HES-v1 health history must retain at most $HES_V1_MAX_LATENCY_SAMPLES latency samples."
        }
        require(this.recentLatencySamplesMillis.all { it >= 0L }) {
            "Latency samples must be non-negative."
        }
        when (circuitState) {
            CircuitState.CLOSED -> {
                require(openedAtEpochMillis == null && nextProbeAtEpochMillis == null) {
                    "CLOSED health state cannot retain an OPEN cooldown."
                }
            }
            CircuitState.OPEN,
            CircuitState.HALF_OPEN,
            -> {
                require(openCount > 0) { "OPEN/HALF_OPEN health state requires a positive openCount." }
                require(openedAtEpochMillis != null && nextProbeAtEpochMillis != null) {
                    "OPEN/HALF_OPEN health state requires cooldown timestamps."
                }
                require(nextProbeAtEpochMillis >= openedAtEpochMillis) {
                    "Health next probe time must not precede the OPEN timestamp."
                }
            }
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

    private fun nearestRankLatency(percentile: Int): Long? {
        if (recentLatencySamplesMillis.size < MIN_LATENCY_SAMPLES_FOR_PERCENTILE) return null
        val sorted = recentLatencySamplesMillis.sorted()
        val rank = ceil(percentile / PERCENTILE_SCALE * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

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
    sealed interface RemoteAttemptObservation : SourceObservation {
        val kind: RemoteAttemptKind
    }

    sealed interface Success : SourceObservation {
        data class Remote(
            override val kind: RemoteAttemptKind,
            val latencyMillis: Long,
        ) : Success, RemoteAttemptObservation {
            init {
                require(latencyMillis >= 0L) { "Remote success latency must be non-negative." }
            }
        }

        data object Local : Success
    }

    sealed interface TransportFailure : SourceObservation, RemoteAttemptObservation {
        data class Timeout(
            override val kind: RemoteAttemptKind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
        ) : TransportFailure

        data class Connection(
            override val kind: RemoteAttemptKind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
        ) : TransportFailure

        data class RateLimited(
            override val kind: RemoteAttemptKind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
        ) : TransportFailure
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

    sealed interface ContentFailure : SourceObservation, RemoteAttemptObservation {
        data class EmptyDocument(
            override val kind: RemoteAttemptKind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
        ) : ContentFailure

        data class InvalidDocument(
            override val kind: RemoteAttemptKind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
        ) : ContentFailure

        data class CorruptDocument(
            override val kind: RemoteAttemptKind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
        ) : ContentFailure
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

val SourceObservation.penalizesSourceHealth: Boolean
    get() = this is SourceObservation.TransportFailure || this is SourceObservation.ContentFailure

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

    companion object {
        fun v1(): SourceHealthReducer = DefaultSourceHealthReducer()
    }
}
