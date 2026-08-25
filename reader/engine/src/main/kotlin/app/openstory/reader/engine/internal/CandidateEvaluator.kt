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
        if (policy.languageOrder.isEmpty()) return BasisPoints(10_000)
        return BasisPoints(
            when (policy.languageOrder.indexOf(normalizeLanguageTag(languageTag))) {
                0 -> 10_000
                1 -> 8_000
                2 -> 6_000
                in 3..Int.MAX_VALUE -> 4_000
                else -> 2_000
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
            candidate.releaseId == continuity.targetResumeReleaseId -> 10_000
            continuity.committedChapterId == snapshot.targetChapterId &&
                candidate.releaseId == continuity.committedReleaseId -> 10_000
            continuity.committedSourceGroupKey != null &&
                candidate.sourceGroupKey == continuity.committedSourceGroupKey -> 8_000
            continuity.committedSourceId != null && candidate.sourceId == continuity.committedSourceId -> 6_500
            committedLanguage != null && normalizedLanguage == committedLanguage -> 2_000
            else -> 0
        }
        return BasisPoints(score)
    }

    private fun freshnessScore(
        publishedAtEpochMillis: Long?,
        newestPublishedAtEpochMillis: Long?,
    ): BasisPoints {
        if (publishedAtEpochMillis == null || newestPublishedAtEpochMillis == null) return BasisPoints(5_000)
        val age = (newestPublishedAtEpochMillis - publishedAtEpochMillis).coerceAtLeast(0L)
        return BasisPoints(
            when {
                age <= HOUR_MILLIS -> 10_000
                age <= DAY_MILLIS -> 9_000
                age <= WEEK_MILLIS -> 7_500
                age <= MONTH_MILLIS -> 6_000
                else -> 4_000
            },
        )
    }

    private fun localFeatures(localAccess: CandidateLocalAccess): AccessFeatureVector = AccessFeatureVector(
        health = BasisPoints(10_000),
        reliability = BasisPoints(10_000),
        latency = BasisPoints(10_000),
        cacheUtility = BasisPoints(
            when (localAccess) {
                is CandidateLocalAccess.AvailableExact -> 10_000
                is CandidateLocalAccess.AvailableUnverified -> 6_000
                else -> error("LOCAL-preferred candidate must carry a usable local access fact.")
            },
        ),
    )

    private fun remoteFeatures(snapshot: SourceHealthSnapshot?): AccessFeatureVector {
        val state = snapshot?.state ?: SourceHealthState()
        val health = when (state.circuitState) {
            CircuitState.CLOSED -> 10_000
            CircuitState.HALF_OPEN -> 6_000
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
        val p50 = state.p50LatencyMillis ?: return BasisPoints(5_000)
        return BasisPoints(
            when {
                p50 <= 250L -> 10_000
                p50 <= 500L -> 8_500
                p50 <= 1_000L -> 6_500
                p50 <= 2_000L -> 4_000
                p50 <= 4_000L -> 2_000
                else -> 1_000
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
        const val TOTAL_WEIGHT = 10_000L
        const val HOUR_MILLIS = 60L * 60L * 1000L
        const val DAY_MILLIS = 24L * HOUR_MILLIS
        const val WEEK_MILLIS = 7L * DAY_MILLIS
        const val MONTH_MILLIS = 30L * DAY_MILLIS
    }
}
