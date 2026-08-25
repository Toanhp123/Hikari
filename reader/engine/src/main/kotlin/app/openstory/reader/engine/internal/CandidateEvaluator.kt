package app.openstory.reader.engine.internal

import app.openstory.reader.engine.AccessFeatureVector
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.BasisPoints
import app.openstory.reader.engine.CandidateLocalAccess
import app.openstory.reader.engine.CircuitState
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot
import app.openstory.reader.engine.RoutingCandidate
import app.openstory.reader.engine.SemanticFeatureVector
import app.openstory.reader.engine.SourceHealthSnapshot
import app.openstory.reader.engine.SourceHealthState
import app.openstory.reader.engine.normalizeLanguageTag

internal data class EvaluatedCandidate(
    val candidate: RoutingCandidate,
    val localFingerprint: String?,
    val remoteEligible: Boolean,
    val preferredAccessMode: AccessMode,
    val semanticFeatures: SemanticFeatureVector,
    val preferredAccessFeatures: AccessFeatureVector,
    val weightedScore: BasisPoints,
    val remoteAccessScore: BasisPoints?,
    val remoteReliability: BasisPoints?,
    val remoteCircuitState: CircuitState?,
)

/** Converts immutable routing facts into exact fixed-point feature vectors. */
internal class CandidateEvaluator {
    fun evaluate(
        eligible: List<EligibleRoutingCandidate>,
        snapshot: ReaderRoutingSnapshot,
        policy: ReaderRoutingPolicy,
    ): List<EvaluatedCandidate> {
        if (eligible.isEmpty()) return emptyList()
        val healthBySource = snapshot.sourceHealth.associateBy { it.key.sourceId }
        val newestPublishedAt = eligible.mapNotNull { it.candidate.publishedAtEpochMillis }.maxOrNull()
        return eligible.map { item ->
            val candidate = item.candidate
            val semantic = SemanticFeatureVector(
                language = languageScore(candidate.languageTag, policy),
                continuity = continuityScore(candidate, snapshot),
                completeness = candidate.completeness,
                freshness = freshnessScore(candidate.publishedAtEpochMillis, newestPublishedAt),
            )
            val remote = if (item.remoteEligible) {
                remoteFeatures(healthBySource[candidate.sourceId])
            } else {
                null
            }
            val preferredMode = if (item.localFingerprint != null) AccessMode.LOCAL else AccessMode.REMOTE
            val preferred = if (preferredMode == AccessMode.LOCAL) {
                localFeatures(candidate.localAccess)
            } else {
                checkNotNull(remote) { "Eligible candidate must expose at least one usable access path." }
            }
            EvaluatedCandidate(
                candidate = candidate,
                localFingerprint = item.localFingerprint,
                remoteEligible = item.remoteEligible,
                preferredAccessMode = preferredMode,
                semanticFeatures = semantic,
                preferredAccessFeatures = preferred,
                weightedScore = weightedScore(semantic, preferred, policy),
                remoteAccessScore = remote?.let { remoteAccessScore(it, policy) },
                remoteReliability = remote?.reliability,
                remoteCircuitState = if (item.remoteEligible) {
                    healthBySource[candidate.sourceId]?.state?.circuitState ?: CircuitState.CLOSED
                } else {
                    null
                },
            )
        }
    }

    private fun languageScore(languageTag: String, policy: ReaderRoutingPolicy): BasisPoints {
        if (policy.languageOrder.isEmpty()) return BasisPoints(FULL_SCORE)
        return BasisPoints(
            when (policy.languagePreferenceRank(languageTag)) {
                0 -> FULL_SCORE
                1 -> LANGUAGE_SECOND_SCORE
                2 -> LANGUAGE_THIRD_SCORE
                in LANGUAGE_ORDERED_FALLBACK_START_RANK..Int.MAX_VALUE -> LANGUAGE_ORDERED_FALLBACK_SCORE
                else -> LANGUAGE_UNPREFERRED_SCORE
            },
        )
    }

    private fun continuityScore(
        candidate: RoutingCandidate,
        snapshot: ReaderRoutingSnapshot,
    ): BasisPoints {
        val continuity = snapshot.continuity
        val normalizedLanguage = normalizeLanguageTag(candidate.languageTag)
        val committedLanguage = continuity.committedLanguageTag?.let(::normalizeLanguageTag)
        val score = when {
            candidate.releaseId == continuity.targetResumeReleaseId -> FULL_SCORE
            continuity.committedChapterId == snapshot.targetChapterId &&
                candidate.releaseId == continuity.committedReleaseId -> FULL_SCORE
            continuity.committedSourceGroupKey != null &&
                candidate.sourceGroupKey == continuity.committedSourceGroupKey -> CONTINUITY_SOURCE_GROUP_SCORE
            continuity.committedSourceId != null &&
                candidate.sourceId == continuity.committedSourceId -> CONTINUITY_SOURCE_SCORE
            committedLanguage != null && normalizedLanguage == committedLanguage -> CONTINUITY_LANGUAGE_SCORE
            else -> 0
        }
        return BasisPoints(score)
    }

    private fun freshnessScore(
        publishedAtEpochMillis: Long?,
        newestPublishedAtEpochMillis: Long?,
    ): BasisPoints {
        if (publishedAtEpochMillis == null || newestPublishedAtEpochMillis == null) {
            return BasisPoints(FRESHNESS_UNKNOWN_SCORE)
        }
        val age = (newestPublishedAtEpochMillis - publishedAtEpochMillis).coerceAtLeast(0L)
        return BasisPoints(
            when {
                age <= HOUR_MILLIS -> FULL_SCORE
                age <= DAY_MILLIS -> FRESHNESS_DAY_SCORE
                age <= WEEK_MILLIS -> FRESHNESS_WEEK_SCORE
                age <= MONTH_MILLIS -> FRESHNESS_MONTH_SCORE
                else -> FRESHNESS_OLDER_SCORE
            },
        )
    }

    private fun localFeatures(localAccess: CandidateLocalAccess): AccessFeatureVector = AccessFeatureVector(
        health = BasisPoints(FULL_SCORE),
        reliability = BasisPoints(FULL_SCORE),
        latency = BasisPoints(FULL_SCORE),
        cacheUtility = BasisPoints(
            when (localAccess) {
                is CandidateLocalAccess.AvailableExact -> FULL_SCORE
                is CandidateLocalAccess.AvailableUnverified -> LOCAL_UNVERIFIED_CACHE_SCORE
                else -> error("LOCAL-preferred candidate must carry a usable local access fact.")
            },
        ),
    )

    private fun remoteFeatures(snapshot: SourceHealthSnapshot?): AccessFeatureVector {
        val state = snapshot?.state ?: SourceHealthState()
        val health = when (state.circuitState) {
            CircuitState.CLOSED -> FULL_SCORE
            CircuitState.HALF_OPEN -> HALF_OPEN_HEALTH_SCORE
            CircuitState.OPEN -> error("OPEN remote paths must be rejected before feature evaluation.")
        }
        return AccessFeatureVector(
            health = BasisPoints(health),
            reliability = state.successEwmaBasisPoints,
            latency = latencyScore(state),
            cacheUtility = BasisPoints(0),
        )
    }

    private fun latencyScore(state: SourceHealthState): BasisPoints {
        val p50 = state.p50LatencyMillis ?: return BasisPoints(LATENCY_UNKNOWN_SCORE)
        return BasisPoints(
            when {
                p50 <= LATENCY_EXCELLENT_MAX_MILLIS -> FULL_SCORE
                p50 <= LATENCY_GOOD_MAX_MILLIS -> LATENCY_GOOD_SCORE
                p50 <= LATENCY_FAIR_MAX_MILLIS -> LATENCY_FAIR_SCORE
                p50 <= LATENCY_SLOW_MAX_MILLIS -> LATENCY_SLOW_SCORE
                p50 <= LATENCY_VERY_SLOW_MAX_MILLIS -> LATENCY_VERY_SLOW_SCORE
                else -> LATENCY_POOR_SCORE
            },
        )
    }

    private fun weightedScore(
        semantic: SemanticFeatureVector,
        access: AccessFeatureVector,
        policy: ReaderRoutingPolicy,
    ): BasisPoints {
        val weights = policy.weights
        val sum = semantic.language.value.toLong() * weights.language.value +
            semantic.continuity.value.toLong() * weights.continuity.value +
            access.health.value.toLong() * weights.health.value +
            access.reliability.value.toLong() * weights.reliability.value +
            semantic.completeness.value.toLong() * weights.completeness.value +
            access.latency.value.toLong() * weights.latency.value +
            semantic.freshness.value.toLong() * weights.freshness.value +
            access.cacheUtility.value.toLong() * weights.cacheUtility.value
        return BasisPoints((sum / TOTAL_WEIGHT).toInt())
    }

    private fun remoteAccessScore(
        access: AccessFeatureVector,
        policy: ReaderRoutingPolicy,
    ): BasisPoints {
        val weights = policy.weights
        val totalAccessWeight = weights.health.value + weights.reliability.value +
            weights.latency.value + weights.cacheUtility.value
        val sum = access.health.value.toLong() * weights.health.value +
            access.reliability.value.toLong() * weights.reliability.value +
            access.latency.value.toLong() * weights.latency.value +
            access.cacheUtility.value.toLong() * weights.cacheUtility.value
        return BasisPoints((sum / totalAccessWeight).toInt())
    }

    private companion object {
        const val FULL_SCORE = BasisPoints.MAX_VALUE

        const val LANGUAGE_ORDERED_FALLBACK_START_RANK = 3
        const val LANGUAGE_SECOND_SCORE = 8_000
        const val LANGUAGE_THIRD_SCORE = 6_000
        const val LANGUAGE_ORDERED_FALLBACK_SCORE = 4_000
        const val LANGUAGE_UNPREFERRED_SCORE = 2_000

        const val CONTINUITY_SOURCE_GROUP_SCORE = 8_000
        const val CONTINUITY_SOURCE_SCORE = 6_500
        const val CONTINUITY_LANGUAGE_SCORE = 2_000

        const val FRESHNESS_UNKNOWN_SCORE = 5_000
        const val FRESHNESS_DAY_SCORE = 9_000
        const val FRESHNESS_WEEK_SCORE = 7_500
        const val FRESHNESS_MONTH_SCORE = 6_000
        const val FRESHNESS_OLDER_SCORE = 4_000

        const val LOCAL_UNVERIFIED_CACHE_SCORE = 6_000
        const val HALF_OPEN_HEALTH_SCORE = 6_000

        const val LATENCY_UNKNOWN_SCORE = 5_000
        const val LATENCY_EXCELLENT_MAX_MILLIS = 250L
        const val LATENCY_GOOD_MAX_MILLIS = 500L
        const val LATENCY_FAIR_MAX_MILLIS = 1_000L
        const val LATENCY_SLOW_MAX_MILLIS = 2_000L
        const val LATENCY_VERY_SLOW_MAX_MILLIS = 4_000L
        const val LATENCY_GOOD_SCORE = 8_500
        const val LATENCY_FAIR_SCORE = 6_500
        const val LATENCY_SLOW_SCORE = 4_000
        const val LATENCY_VERY_SLOW_SCORE = 2_000
        const val LATENCY_POOR_SCORE = 1_000

        const val TOTAL_WEIGHT = 10_000L
        const val HOUR_MILLIS = 60L * 60L * 1000L
        const val DAY_MILLIS = 24L * HOUR_MILLIS
        const val WEEK_MILLIS = 7L * DAY_MILLIS
        const val MONTH_MILLIS = 30L * DAY_MILLIS
    }
}
