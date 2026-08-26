package app.openstory.reader.engine

enum class LanguageFallbackMode {
    ORDERED_ALLOW,
    STRICT_ALLOWED,
}

private object ReaderRoutingDefaults {
    const val LANGUAGE_WEIGHT = 2_500
    const val CONTINUITY_WEIGHT = 2_500
    const val HEALTH_WEIGHT = 1_800
    const val RELIABILITY_WEIGHT = 1_000
    const val COMPLETENESS_WEIGHT = 900
    const val LATENCY_WEIGHT = 700
    const val FRESHNESS_WEIGHT = 300
    const val CACHE_UTILITY_WEIGHT = 300
    const val REQUIRED_WEIGHT_TOTAL = BasisPoints.MAX_VALUE
    const val HEDGE_ALTERNATE_MINIMUM_REMOTE_ACCESS_SCORE = 8_000
    const val HEDGE_ALTERNATE_MINIMUM_RELIABILITY = 9_000
    const val MAX_RECOVERY_ATTEMPTS = 6
    const val MAX_PLANNED_FOREGROUND_REMOTE_ATTEMPTS = 4
    const val NORMAL_SWITCH_THRESHOLD = 800
    const val DEGRADED_SWITCH_THRESHOLD = 350
}

data class ReaderRoutingWeights(
    val language: BasisPoints = BasisPoints(ReaderRoutingDefaults.LANGUAGE_WEIGHT),
    val continuity: BasisPoints = BasisPoints(ReaderRoutingDefaults.CONTINUITY_WEIGHT),
    val health: BasisPoints = BasisPoints(ReaderRoutingDefaults.HEALTH_WEIGHT),
    val reliability: BasisPoints = BasisPoints(ReaderRoutingDefaults.RELIABILITY_WEIGHT),
    val completeness: BasisPoints = BasisPoints(ReaderRoutingDefaults.COMPLETENESS_WEIGHT),
    val latency: BasisPoints = BasisPoints(ReaderRoutingDefaults.LATENCY_WEIGHT),
    val freshness: BasisPoints = BasisPoints(ReaderRoutingDefaults.FRESHNESS_WEIGHT),
    val cacheUtility: BasisPoints = BasisPoints(ReaderRoutingDefaults.CACHE_UTILITY_WEIGHT),
) {
    val total: Int = listOf(
        language,
        continuity,
        health,
        reliability,
        completeness,
        latency,
        freshness,
        cacheUtility,
    ).sumOf(BasisPoints::value)

    internal val totalRemoteAccessWeight: Int =
        health.value + reliability.value + latency.value + cacheUtility.value

    init {
        require(total == ReaderRoutingDefaults.REQUIRED_WEIGHT_TOTAL) {
            "Reader routing weights must total exactly 10_000 basis points: $total"
        }
        require(totalRemoteAccessWeight > 0) {
            "Reader routing REMOTE access weights must contain at least one positive weight."
        }
    }
}

data class HedgePolicy(
    val delayMillis: Long = 650L,
    val primaryP95ThresholdMillis: Long = 1_200L,
    val minimumLatencySamples: Int = 3,
    val alternateMinimumRemoteAccessScore: BasisPoints = BasisPoints(
        ReaderRoutingDefaults.HEDGE_ALTERNATE_MINIMUM_REMOTE_ACCESS_SCORE,
    ),
    val alternateMinimumReliability: BasisPoints = BasisPoints(
        ReaderRoutingDefaults.HEDGE_ALTERNATE_MINIMUM_RELIABILITY,
    ),
) {
    init {
        require(delayMillis >= 0L) { "Hedge delay must be non-negative." }
        require(primaryP95ThresholdMillis >= 0L) {
            "Hedge primary p95 threshold must be non-negative."
        }
        require(minimumLatencySamples >= 0) {
            "Hedge minimum latency sample count must be non-negative."
        }
    }
}

/**
 * Versioned immutable routing policy.
 *
 * This is intentionally not a data class: a generated public `copy(...)` would be an alternate
 * construction path that could bypass language normalization and defensive list copying.
 */
class ReaderRoutingPolicy private constructor(
    val hesContractVersion: HesContractVersion,
    val algorithmVersion: ReaderRoutingAlgorithmVersion,
    val version: ReaderPolicyVersion,
    val weights: ReaderRoutingWeights,
    languageOrder: List<String>,
    val languageFallbackMode: LanguageFallbackMode,
    val normalSwitchThreshold: BasisPoints,
    val degradedSwitchThreshold: BasisPoints,
    val allowUnverifiedLocalAttempt: Boolean,
    val maxRecoveryAttempts: Int,
    val maxPlannedForegroundRemoteAttempts: Int,
    val hedge: HedgePolicy,
) {
    val languageOrder: List<String> = languageOrder.toList()
    private val languagePreferenceRanks: Map<String, Int> = this.languageOrder
        .mapIndexed { index, languageTag -> languageTag to index }
        .toMap()

    init {
        require(this.languageOrder.none(String::isBlank)) {
            "languageOrder must not contain blank language tags."
        }
        require(this.languageOrder.all { it == normalizeLanguageTag(it) }) {
            "languageOrder must contain normalized language tags only."
        }
        require(this.languageOrder.size == this.languageOrder.toSet().size) {
            "languageOrder must not contain duplicate normalized language tags."
        }
        if (languageFallbackMode == LanguageFallbackMode.STRICT_ALLOWED) {
            require(this.languageOrder.isNotEmpty()) {
                "STRICT_ALLOWED requires at least one language tag."
            }
        }
        require(maxRecoveryAttempts in 0..ReaderRoutingDefaults.MAX_RECOVERY_ATTEMPTS) {
            "maxRecoveryAttempts must be in 0..${ReaderRoutingDefaults.MAX_RECOVERY_ATTEMPTS}: " +
                maxRecoveryAttempts
        }
        require(
            maxPlannedForegroundRemoteAttempts in
                1..ReaderRoutingDefaults.MAX_PLANNED_FOREGROUND_REMOTE_ATTEMPTS
        ) {
            "maxPlannedForegroundRemoteAttempts must be in " +
                "1..${ReaderRoutingDefaults.MAX_PLANNED_FOREGROUND_REMOTE_ATTEMPTS}: " +
                maxPlannedForegroundRemoteAttempts
        }
    }

    internal fun languagePreferenceRank(languageTag: String): Int? =
        languagePreferenceRanks[normalizeLanguageTag(languageTag)]

    internal fun isLanguageAllowed(languageTag: String): Boolean =
        normalizeLanguageTag(languageTag) in languagePreferenceRanks

    override fun equals(other: Any?): Boolean =
        other is ReaderRoutingPolicy &&
            hesContractVersion == other.hesContractVersion &&
            algorithmVersion == other.algorithmVersion &&
            version == other.version &&
            weights == other.weights &&
            languageOrder == other.languageOrder &&
            languageFallbackMode == other.languageFallbackMode &&
            normalSwitchThreshold == other.normalSwitchThreshold &&
            degradedSwitchThreshold == other.degradedSwitchThreshold &&
            allowUnverifiedLocalAttempt == other.allowUnverifiedLocalAttempt &&
            maxRecoveryAttempts == other.maxRecoveryAttempts &&
            maxPlannedForegroundRemoteAttempts == other.maxPlannedForegroundRemoteAttempts &&
            hedge == other.hedge

    override fun hashCode(): Int {
        var result = hesContractVersion.hashCode()
        result = 31 * result + algorithmVersion.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + weights.hashCode()
        result = 31 * result + languageOrder.hashCode()
        result = 31 * result + languageFallbackMode.hashCode()
        result = 31 * result + normalSwitchThreshold.hashCode()
        result = 31 * result + degradedSwitchThreshold.hashCode()
        result = 31 * result + allowUnverifiedLocalAttempt.hashCode()
        result = 31 * result + maxRecoveryAttempts
        result = 31 * result + maxPlannedForegroundRemoteAttempts
        result = 31 * result + hedge.hashCode()
        return result
    }

    override fun toString(): String =
        "ReaderRoutingPolicy(hesContractVersion=$hesContractVersion, " +
            "algorithmVersion=$algorithmVersion, version=$version, weights=$weights, " +
            "languageOrder=$languageOrder, languageFallbackMode=$languageFallbackMode, " +
            "normalSwitchThreshold=$normalSwitchThreshold, " +
            "degradedSwitchThreshold=$degradedSwitchThreshold, " +
            "allowUnverifiedLocalAttempt=$allowUnverifiedLocalAttempt, " +
            "maxRecoveryAttempts=$maxRecoveryAttempts, " +
            "maxPlannedForegroundRemoteAttempts=$maxPlannedForegroundRemoteAttempts, hedge=$hedge)"

    companion object {
        fun v1(
            weights: ReaderRoutingWeights = ReaderRoutingWeights(),
            languageOrder: List<String> = emptyList(),
            languageFallbackMode: LanguageFallbackMode = LanguageFallbackMode.ORDERED_ALLOW,
            normalSwitchThreshold: BasisPoints = BasisPoints(ReaderRoutingDefaults.NORMAL_SWITCH_THRESHOLD),
            degradedSwitchThreshold: BasisPoints = BasisPoints(ReaderRoutingDefaults.DEGRADED_SWITCH_THRESHOLD),
            allowUnverifiedLocalAttempt: Boolean = true,
            maxRecoveryAttempts: Int = ReaderRoutingDefaults.MAX_RECOVERY_ATTEMPTS,
            maxPlannedForegroundRemoteAttempts: Int = ReaderRoutingDefaults.MAX_PLANNED_FOREGROUND_REMOTE_ATTEMPTS,
            hedge: HedgePolicy = HedgePolicy(),
        ): ReaderRoutingPolicy {
            val normalizedLanguageOrder = languageOrder.map(::normalizeLanguageTag)
            return ReaderRoutingPolicy(
                hesContractVersion = HesContractVersion.HES_V1,
                algorithmVersion = ReaderRoutingAlgorithmVersion.READER_ROUTING_V1,
                version = ReaderPolicyVersion.READER_POLICY_V1,
                weights = weights,
                languageOrder = normalizedLanguageOrder,
                languageFallbackMode = languageFallbackMode,
                normalSwitchThreshold = normalSwitchThreshold,
                degradedSwitchThreshold = degradedSwitchThreshold,
                allowUnverifiedLocalAttempt = allowUnverifiedLocalAttempt,
                maxRecoveryAttempts = maxRecoveryAttempts,
                maxPlannedForegroundRemoteAttempts = maxPlannedForegroundRemoteAttempts,
                hedge = hedge,
            )
        }
    }
}
