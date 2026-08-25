package app.openstory.reader.engine.internal

import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.BasisPoints
import app.openstory.reader.engine.CandidateLocalAccess
import app.openstory.reader.engine.CandidateRejection
import app.openstory.reader.engine.CandidateRemoteAccess
import app.openstory.reader.engine.CompetitiveSet
import app.openstory.reader.engine.DecisionReason
import app.openstory.reader.engine.DiagnosticNote
import app.openstory.reader.engine.HedgeDirective
import app.openstory.reader.engine.HedgeOmissionReason
import app.openstory.reader.engine.IncumbentKind
import app.openstory.reader.engine.ReaderDecisionTrace
import app.openstory.reader.engine.ReaderRouteDecision
import app.openstory.reader.engine.ReaderRouteEngine
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot
import app.openstory.reader.engine.RejectionCode
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.engine.RoutingCandidate
import app.openstory.reader.engine.SourceGroupKey
import app.openstory.reader.engine.normalizeLanguageTag
import app.openstory.common.id.ChapterReleaseId

/**
 * M1-only deterministic compatibility reasoner.
 *
 * It intentionally mirrors only the overlap that the legacy ReleaseSelector and HES facts can both
 * represent. Adaptive eligibility/scoring, local routing, health, hysteresis, budgets, prefetch,
 * and hedging are introduced by later milestones.
 */
internal class DefaultReaderRouteEngine : ReaderRouteEngine {
    override fun plan(
        snapshot: ReaderRoutingSnapshot,
        policy: ReaderRoutingPolicy,
    ): ReaderRouteDecision {
        val canonicalCandidates = snapshot.candidates
            .sortedWith(compareBy({ it.sourceId.value }, { it.releaseId.value }))
        require(canonicalCandidates.all { it.remoteAccess == CandidateRemoteAccess.PERMITTED }) {
            "M1 compatibility planning supports only REMOTE-permitted candidates; " +
                "exact access eligibility belongs to M4."
        }
        require(canonicalCandidates.none(::hasUsableLocalPath)) {
            "M1 compatibility planning supports REMOTE-only usable paths; local access planning belongs to M4."
        }
        val ranked = canonicalCandidates.sortedWith(compatibilityComparator(snapshot, policy))
        val rejections = emptyList<CandidateRejection>()
        val attempts = ranked.mapIndexed { index, candidate ->
            RouteAttempt(
                attemptId = "attempt-$index",
                releaseId = candidate.releaseId,
                sourceId = candidate.sourceId,
                accessMode = AccessMode.REMOTE,
                localFingerprint = null,
                role = if (index == 0) AttemptRole.PRIMARY else AttemptRole.FALLBACK,
            )
        }
        val primary = attempts.firstOrNull()
        val hedgeDirective = HedgeDirective.Omitted(HedgeOmissionReason.NOT_EVALUATED)
        val reason = finalReason(primary?.releaseId, snapshot)
        val resumeIncumbent = snapshot.continuity.targetResumeReleaseId?.let { releaseId ->
            ranked.firstOrNull { it.releaseId == releaseId }
        }
        val diagnostics = if (
            snapshot.explicitReleaseId != null &&
            canonicalCandidates.none { it.releaseId == snapshot.explicitReleaseId }
        ) {
            listOf(DiagnosticNote(RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT))
        } else {
            emptyList()
        }
        val trace = ReaderDecisionTrace(
            hesContractVersion = policy.hesContractVersion,
            algorithmVersion = policy.algorithmVersion,
            policyVersion = policy.version,
            planRevision = snapshot.planRevision,
            chapterGraphRevision = snapshot.chapterGraphRevision,
            canonicalCandidateIds = canonicalCandidates.map { it.releaseId },
            rejections = rejections,
            diagnostics = diagnostics,
            candidateEvaluations = emptyList(),
            stableRanking = ranked.map { it.releaseId },
            incumbentReleaseId = resumeIncumbent?.releaseId,
            incumbentKind = if (resumeIncumbent == null) {
                IncumbentKind.NONE
            } else {
                IncumbentKind.TARGET_RESUME_RELEASE
            },
            rawChallengerReleaseId = ranked.firstOrNull()?.releaseId,
            switchAdvantage = null,
            requiredHysteresisThreshold = null,
            finalWinnerReleaseId = primary?.releaseId,
            routeConstruction = attempts,
            hedgeDirective = hedgeDirective,
            finalDecisionReason = reason,
            healthOrigins = emptyList(),
        )

        return ReaderRouteDecision(
            hesContractVersion = policy.hesContractVersion,
            algorithmVersion = policy.algorithmVersion,
            policyVersion = policy.version,
            planRevision = snapshot.planRevision,
            competitiveSet = CompetitiveSet(primary = primary, hedge = null),
            hedgeDirective = hedgeDirective,
            recoveryChain = attempts.drop(1),
            rejections = rejections,
            trace = trace,
            confidence = BasisPoints(0),
            reason = reason,
        )
    }

    private fun compatibilityComparator(
        snapshot: ReaderRoutingSnapshot,
        policy: ReaderRoutingPolicy,
    ): Comparator<RoutingCandidate> {
        val continuity = snapshot.continuity
        return compareByDescending<RoutingCandidate> { it.releaseId == snapshot.explicitReleaseId }
            .thenByDescending { it.releaseId == continuity.targetResumeReleaseId }
            .thenByDescending { matchesGroup(it, continuity.committedSourceGroupKey) }
            .thenByDescending { it.sourceId == continuity.committedSourceId }
            .thenBy { languageRank(it.languageTag, policy.languageOrder) }
            .thenByDescending { it.completeness.value }
            .thenByDescending { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
            .thenBy { it.sourceId.value }
            .thenBy { it.releaseId.value }
    }

    private fun languageRank(languageTag: String, order: List<String>): Int {
        val rank = order.indexOf(normalizeLanguageTag(languageTag))
        return if (rank >= 0) rank else Int.MAX_VALUE
    }

    private fun matchesGroup(candidate: RoutingCandidate, group: SourceGroupKey?): Boolean =
        group != null && candidate.sourceGroupKey == group

    private fun finalReason(
        winnerReleaseId: ChapterReleaseId?,
        snapshot: ReaderRoutingSnapshot,
    ): DecisionReason = when {
        winnerReleaseId == null -> DecisionReason.NO_ELIGIBLE_CANDIDATE
        winnerReleaseId == snapshot.explicitReleaseId -> DecisionReason.EXPLICIT_ELIGIBLE_RELEASE
        winnerReleaseId == snapshot.continuity.targetResumeReleaseId ->
            DecisionReason.TARGET_RESUME_INCUMBENT_RETAINED
        else -> DecisionReason.TOP_RANKED_NO_INCUMBENT
    }

    private fun hasUsableLocalPath(candidate: RoutingCandidate): Boolean = when (candidate.localAccess) {
        is CandidateLocalAccess.AvailableExact,
        is CandidateLocalAccess.AvailableUnverified,
        -> true
        CandidateLocalAccess.Unknown,
        CandidateLocalAccess.Miss,
        is CandidateLocalAccess.KnownInvalid,
        -> false
    }
}
