package app.openstory.reader.engine

enum class LanguageFallbackMode {
    ORDERED_ALLOW,
    STRICT_ALLOWED,
}

data class ReaderRoutingWeights(
    val language: BasisPoints = BasisPoints(2_500),
    val continuity: BasisPoints = BasisPoints(2_500),
    val health: BasisPoints = BasisPoints(1_800),
    val reliability: BasisPoints = BasisPoints(1_000),
    val completeness: BasisPoints = BasisPoints(900),
    val latency: BasisPoints = BasisPoints(700),
    val freshness: BasisPoints = BasisPoints(300),
    val cacheUtility: BasisPoints = BasisPoints(300),
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

    init {
        require(total == 10_000) {
            "Reader routing weights must total exactly 10_000 basis points: $total"
        }
    }
}

data class HedgePolicy(
    val delayMillis: Long = 650L,
    val primaryP95ThresholdMillis: Long = 1_200L,
    val minimumLatencySamples: Int = 3,
    val alternateMinimumRemoteAccessScore: BasisPoints = BasisPoints(8_000),
    val alternateMinimumReliability: BasisPoints = BasisPoints(9_000),
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
        require(maxRecoveryAttempts in 0..6) {
            "maxRecoveryAttempts must be in 0..6: $maxRecoveryAttempts"
        }
        require(maxPlannedForegroundRemoteAttempts in 1..4) {
            "maxPlannedForegroundRemoteAttempts must be in 1..4: " +
                maxPlannedForegroundRemoteAttempts
        }
    }

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
            normalSwitchThreshold: BasisPoints = BasisPoints(800),
            degradedSwitchThreshold: BasisPoints = BasisPoints(350),
            allowUnverifiedLocalAttempt: Boolean = true,
            maxRecoveryAttempts: Int = 6,
            maxPlannedForegroundRemoteAttempts: Int = 4,
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
