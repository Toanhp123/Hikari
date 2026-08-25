package app.openstory.reader.engine.internal

import app.openstory.reader.engine.BasisPoints
import app.openstory.reader.engine.CandidateEvaluationTrace
import app.openstory.reader.engine.CompetitiveSet
import app.openstory.reader.engine.HealthOriginTrace
import app.openstory.reader.engine.HedgeDirective
import app.openstory.reader.engine.ReaderDecisionTrace
import app.openstory.reader.engine.ReaderRouteDecision
import app.openstory.reader.engine.ReaderRouteEngine
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot

/** HES-v1 pure adaptive routing pipeline. */
internal class DefaultReaderRouteEngine : ReaderRouteEngine {
    private val eligibility = EligibilityEvaluator()
    private val evaluator = CandidateEvaluator()
    private val ranker = CandidateRanker()
    private val arbiter = ContinuityArbiter()
    private val routePlanner = RoutePlanner()

    override fun plan(
        snapshot: ReaderRoutingSnapshot,
        policy: ReaderRoutingPolicy,
    ): ReaderRouteDecision {
        val eligibilityResult = eligibility.evaluate(snapshot, policy)
        val evaluations = evaluator.evaluate(eligibilityResult.eligible, snapshot, policy)
        val ranked = ranker.rank(evaluations)
        val arbitration = arbiter.choose(ranked, snapshot, policy)
        val route = routePlanner.plan(ranked, arbitration.winner, policy, snapshot)
        val primary = route.attempts.firstOrNull()
        val hedge = route.hedgeDirective
        val hedgeAttempt = (hedge as? HedgeDirective.Launch)?.attempt
        val recovery = route.attempts.drop(1).filter { it != hedgeAttempt }
        val evaluationByRelease = evaluations.associateBy { it.candidate.releaseId }
        val trace = ReaderDecisionTrace(
            hesContractVersion = policy.hesContractVersion,
            algorithmVersion = policy.algorithmVersion,
            policyVersion = policy.version,
            planRevision = snapshot.planRevision,
            chapterGraphRevision = snapshot.chapterGraphRevision,
            canonicalCandidateIds = snapshot.candidates.map { it.releaseId },
            rejections = eligibilityResult.rejections,
            diagnostics = eligibilityResult.diagnostics,
            candidateEvaluations = snapshot.candidates.map { candidate ->
                val evaluated = evaluationByRelease[candidate.releaseId]
                CandidateEvaluationTrace(
                    releaseId = candidate.releaseId,
                    semanticFeatures = evaluated?.semanticFeatures,
                    preferredAccessFeatures = evaluated?.preferredAccessFeatures,
                    semanticWeightedScore = evaluated?.weightedScore,
                    remoteAccessScore = evaluated?.remoteAccessScore,
                )
            },
            stableRanking = ranked.map { it.candidate.releaseId },
            incumbentReleaseId = arbitration.incumbent?.candidate?.releaseId,
            incumbentKind = arbitration.incumbentKind,
            rawChallengerReleaseId = arbitration.rawChallenger?.candidate?.releaseId,
            switchAdvantage = arbitration.switchAdvantage,
            requiredHysteresisThreshold = arbitration.requiredThreshold,
            finalWinnerReleaseId = arbitration.winner?.candidate?.releaseId,
            routeConstruction = route.attempts,
            hedgeDirective = hedge,
            finalDecisionReason = arbitration.reason,
            healthOrigins = snapshot.sourceHealth.map { HealthOriginTrace(it.key.sourceId, it.origin) },
        )
        return ReaderRouteDecision(
            hesContractVersion = policy.hesContractVersion,
            algorithmVersion = policy.algorithmVersion,
            policyVersion = policy.version,
            planRevision = snapshot.planRevision,
            competitiveSet = CompetitiveSet(primary = primary, hedge = hedgeAttempt),
            hedgeDirective = hedge,
            recoveryChain = recovery,
            rejections = eligibilityResult.rejections,
            trace = trace,
            confidence = confidence(arbitration.winner, ranked),
            reason = arbitration.reason,
        )
    }

    private fun confidence(
        winner: EvaluatedCandidate?,
        ranked: List<EvaluatedCandidate>,
    ): BasisPoints = when {
        winner == null -> BasisPoints(BasisPoints.MIN_VALUE)
        ranked.size == 1 -> winner.weightedScore
        else -> confidenceAgainstBestAlternative(winner, ranked)
    }

    private fun confidenceAgainstBestAlternative(
        winner: EvaluatedCandidate,
        ranked: List<EvaluatedCandidate>,
    ): BasisPoints = ranked
        .firstOrNull { it.candidate.releaseId != winner.candidate.releaseId }
        ?.let { bestAlternative ->
            BasisPoints(
                (CONFIDENCE_MIDPOINT + winner.weightedScore.value - bestAlternative.weightedScore.value)
                    .coerceIn(BasisPoints.MIN_VALUE, BasisPoints.MAX_VALUE),
            )
        }
        ?: winner.weightedScore

    private companion object {
        const val CONFIDENCE_MIDPOINT = BasisPoints.MAX_VALUE / 2
    }
}
