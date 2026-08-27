package app.openstory.reader.engine

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId

data class RouteAttempt(
    val attemptId: String,
    val releaseId: ChapterReleaseId,
    val sourceId: PluginId,
    val accessMode: AccessMode,
    val localFingerprint: String?,
    val role: AttemptRole,
) {
    init {
        require(attemptId.isNotBlank()) { "attemptId must not be blank." }
        when (accessMode) {
            AccessMode.LOCAL -> require(!localFingerprint.isNullOrBlank()) {
                "LOCAL route attempts require a non-blank local fingerprint."
            }
            AccessMode.REMOTE -> require(localFingerprint == null) {
                "REMOTE route attempts must not carry a local fingerprint."
            }
        }
    }
}

data class CompetitiveSet(
    val primary: RouteAttempt?,
    val hedge: RouteAttempt?,
) {
    init {
        require(primary == null || primary.role == AttemptRole.PRIMARY) {
            "Competitive primary must use PRIMARY role."
        }
        require(hedge == null || hedge.role == AttemptRole.HEDGE) {
            "Competitive hedge must use HEDGE role."
        }
        require(primary != null || hedge == null) { "A hedge requires a primary attempt." }
        if (primary != null && hedge != null) {
            require(primary.sourceId != hedge.sourceId) {
                "Primary and hedge must use different sourceId values."
            }
        }
    }
}

enum class HedgeOmissionReason {
    NOT_ELIGIBLE,
}

sealed interface HedgeDirective {
    data class Launch(
        val attempt: RouteAttempt,
        val delayMillis: Long,
    ) : HedgeDirective {
        init {
            require(attempt.role == AttemptRole.HEDGE) { "Hedge directive attempt must use HEDGE role." }
            require(delayMillis >= 0L) { "Hedge delay must be non-negative." }
        }
    }

    data class Omitted(
        val reason: HedgeOmissionReason,
    ) : HedgeDirective
}

enum class DecisionReason {
    EXPLICIT_ELIGIBLE_RELEASE,
    TOP_RANKED_NO_INCUMBENT,
    TARGET_RESUME_INCUMBENT_RETAINED,
    INCUMBENT_RETAINED_BY_HYSTERESIS,
    CHALLENGER_EXCEEDED_SWITCH_THRESHOLD,
    INCUMBENT_UNAVAILABLE,
    NO_ELIGIBLE_CANDIDATE,
}

enum class RejectionCode {
    LOCAL_COPY_KNOWN_INVALID,
    REMOTE_SOURCE_DISABLED_OR_UNAVAILABLE,
    REMOTE_NETWORK_UNAVAILABLE,
    REMOTE_CIRCUIT_OPEN,
    HALF_OPEN_PROBE_NOT_PERMITTED,
    LANGUAGE_FORBIDDEN,
    NO_USABLE_ACCESS_PATH,
    EXPLICIT_RELEASE_NOT_PRESENT,
}

data class CandidateRejection(
    val releaseId: ChapterReleaseId,
    val sourceId: PluginId,
    val accessMode: AccessMode?,
    val code: RejectionCode,
) {
    init {
        require(code != RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT) {
            "EXPLICIT_RELEASE_NOT_PRESENT is a diagnostic note, not a candidate rejection."
        }
    }
}

data class DiagnosticNote(
    val code: RejectionCode,
    val detail: String? = null,
) {
    init {
        require(detail == null || detail.isNotBlank()) { "Diagnostic detail must not be blank." }
    }
}

data class SemanticFeatureVector(
    val language: BasisPoints,
    val continuity: BasisPoints,
    val completeness: BasisPoints,
    val freshness: BasisPoints,
)

data class AccessFeatureVector(
    val health: BasisPoints,
    val reliability: BasisPoints,
    val latency: BasisPoints,
    val cacheUtility: BasisPoints,
)

data class CandidateEvaluationTrace(
    val releaseId: ChapterReleaseId,
    val semanticFeatures: SemanticFeatureVector?,
    val preferredAccessFeatures: AccessFeatureVector?,
    val semanticWeightedScore: BasisPoints?,
    val remoteAccessScore: BasisPoints?,
)

enum class IncumbentKind {
    SAME_TARGET_COMMITTED_RELEASE,
    TARGET_RESUME_RELEASE,
    TRUSTED_SOURCE_GROUP,
    COMMITTED_SOURCE,
    NONE,
}

data class HealthOriginTrace(
    val sourceId: PluginId,
    val origin: SourceHealthOrigin,
)

data class ReaderDecisionTrace(
    val hesContractVersion: HesContractVersion,
    val algorithmVersion: ReaderRoutingAlgorithmVersion,
    val policyVersion: ReaderPolicyVersion,
    val planRevision: ReaderPlanRevision,
    val chapterGraphRevision: ReaderChapterGraphRevision,
    val canonicalCandidateIds: List<ChapterReleaseId>,
    val rejections: List<CandidateRejection>,
    val diagnostics: List<DiagnosticNote>,
    val candidateEvaluations: List<CandidateEvaluationTrace>,
    val stableRanking: List<ChapterReleaseId>,
    val incumbentReleaseId: ChapterReleaseId?,
    val incumbentKind: IncumbentKind,
    val rawChallengerReleaseId: ChapterReleaseId?,
    val switchAdvantage: BasisPoints?,
    val requiredHysteresisThreshold: BasisPoints?,
    val finalWinnerReleaseId: ChapterReleaseId?,
    val routeConstruction: List<RouteAttempt>,
    val hedgeDirective: HedgeDirective,
    val finalDecisionReason: DecisionReason,
    val healthOrigins: List<HealthOriginTrace>,
)

data class ReaderRouteDecision(
    val hesContractVersion: HesContractVersion,
    val algorithmVersion: ReaderRoutingAlgorithmVersion,
    val policyVersion: ReaderPolicyVersion,
    val planRevision: ReaderPlanRevision,
    val competitiveSet: CompetitiveSet,
    val hedgeDirective: HedgeDirective,
    val recoveryChain: List<RouteAttempt>,
    val rejections: List<CandidateRejection>,
    val trace: ReaderDecisionTrace,
    val confidence: BasisPoints,
    val reason: DecisionReason,
) {
    init {
        require(trace.planRevision == planRevision) {
            "Decision and trace must use the same ReaderPlanRevision."
        }
        require(trace.hesContractVersion == hesContractVersion) {
            "Decision and trace HES contract versions must match."
        }
        require(trace.algorithmVersion == algorithmVersion) {
            "Decision and trace algorithm versions must match."
        }
        require(trace.policyVersion == policyVersion) {
            "Decision and trace policy versions must match."
        }
        require(trace.finalDecisionReason == reason) {
            "Decision and trace final reasons must match."
        }
        require(trace.rejections == rejections) {
            "Decision and trace rejection lists must match."
        }
        when (val directive = hedgeDirective) {
            is HedgeDirective.Launch -> require(
                competitiveSet.hedge == directive.attempt,
            ) {
                "A launch hedge directive must reference the competitive hedge attempt."
            }
            is HedgeDirective.Omitted -> require(competitiveSet.hedge == null) {
                "An omitted hedge directive cannot accompany a competitive hedge attempt."
            }
        }
        require(trace.hedgeDirective == hedgeDirective) {
            "Execution hedge directive and trace hedge directive must match."
        }
        val attempts = buildList {
            competitiveSet.primary?.let(::add)
            competitiveSet.hedge?.let(::add)
            addAll(recoveryChain)
        }
        require(attempts.map { it.attemptId }.distinct().size == attempts.size) {
            "Route attempt IDs must be unique within one decision."
        }
        require(trace.routeConstruction == attempts) {
            "Trace route construction must mirror the executable attempt order."
        }
    }
}
