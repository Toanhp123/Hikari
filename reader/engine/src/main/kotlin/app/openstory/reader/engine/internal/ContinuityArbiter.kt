package app.openstory.reader.engine.internal

import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.BasisPoints
import app.openstory.reader.engine.CircuitState
import app.openstory.reader.engine.DecisionReason
import app.openstory.reader.engine.IncumbentKind
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot

internal data class ArbitrationResult(
    val winner: EvaluatedCandidate?,
    val reason: DecisionReason,
    val incumbent: EvaluatedCandidate?,
    val incumbentKind: IncumbentKind,
    val rawChallenger: EvaluatedCandidate?,
    val switchAdvantage: BasisPoints?,
    val requiredThreshold: BasisPoints?,
)

/** Deterministic incumbent resolution and automatic hysteresis. */
internal class ContinuityArbiter {
    fun choose(
        ranked: List<EvaluatedCandidate>,
        snapshot: ReaderRoutingSnapshot,
        policy: ReaderRoutingPolicy,
    ): ArbitrationResult {
        if (ranked.isEmpty()) return noEligibleCandidate()
        return explicitSelection(ranked, snapshot) ?: automaticSelection(ranked, snapshot, policy)
    }

    private fun noEligibleCandidate(): ArbitrationResult = ArbitrationResult(
        winner = null,
        reason = DecisionReason.NO_ELIGIBLE_CANDIDATE,
        incumbent = null,
        incumbentKind = IncumbentKind.NONE,
        rawChallenger = null,
        switchAdvantage = null,
        requiredThreshold = null,
    )

    private fun explicitSelection(
        ranked: List<EvaluatedCandidate>,
        snapshot: ReaderRoutingSnapshot,
    ): ArbitrationResult? = snapshot.explicitReleaseId
        ?.let { explicitId -> ranked.firstOrNull { it.candidate.releaseId == explicitId } }
        ?.let { explicit ->
            ArbitrationResult(
                winner = explicit,
                reason = DecisionReason.EXPLICIT_ELIGIBLE_RELEASE,
                incumbent = null,
                incumbentKind = IncumbentKind.NONE,
                rawChallenger = ranked.first(),
                switchAdvantage = null,
                requiredThreshold = null,
            )
        }

    private fun automaticSelection(
        ranked: List<EvaluatedCandidate>,
        snapshot: ReaderRoutingSnapshot,
        policy: ReaderRoutingPolicy,
    ): ArbitrationResult {
        val (incumbent, kind) = resolveIncumbent(ranked, snapshot)
        val raw = ranked.first()
        return if (incumbent == null) {
            ArbitrationResult(
                winner = raw,
                reason = if (hasUnavailableExactIncumbent(snapshot, ranked)) {
                    DecisionReason.INCUMBENT_UNAVAILABLE
                } else {
                    DecisionReason.TOP_RANKED_NO_INCUMBENT
                },
                incumbent = null,
                incumbentKind = IncumbentKind.NONE,
                rawChallenger = raw,
                switchAdvantage = null,
                requiredThreshold = null,
            )
        } else {
            applyHysteresis(raw, incumbent, kind, policy)
        }
    }

    private fun applyHysteresis(
        raw: EvaluatedCandidate,
        incumbent: EvaluatedCandidate,
        kind: IncumbentKind,
        policy: ReaderRoutingPolicy,
    ): ArbitrationResult {
        val threshold = if (isDegradedRemote(incumbent)) {
            policy.degradedSwitchThreshold
        } else {
            policy.normalSwitchThreshold
        }
        val advantage = (raw.weightedScore.value - incumbent.weightedScore.value)
            .coerceIn(BasisPoints.MIN_VALUE, BasisPoints.MAX_VALUE)
        val switch = raw.candidate.releaseId != incumbent.candidate.releaseId && advantage >= threshold.value
        val reason = when {
            switch -> DecisionReason.CHALLENGER_EXCEEDED_SWITCH_THRESHOLD
            kind == IncumbentKind.TARGET_RESUME_RELEASE -> DecisionReason.TARGET_RESUME_INCUMBENT_RETAINED
            else -> DecisionReason.INCUMBENT_RETAINED_BY_HYSTERESIS
        }
        return ArbitrationResult(
            winner = if (switch) raw else incumbent,
            reason = reason,
            incumbent = incumbent,
            incumbentKind = kind,
            rawChallenger = raw,
            switchAdvantage = BasisPoints(advantage),
            requiredThreshold = threshold,
        )
    }

    private fun resolveIncumbent(
        ranked: List<EvaluatedCandidate>,
        snapshot: ReaderRoutingSnapshot,
    ): Pair<EvaluatedCandidate?, IncumbentKind> {
        val continuity = snapshot.continuity
        val sameTargetCommitted = continuity.committedReleaseId
            ?.takeIf { continuity.committedChapterId == snapshot.targetChapterId }
            ?.let { releaseId ->
                matchIncumbent(ranked, IncumbentKind.SAME_TARGET_COMMITTED_RELEASE) {
                    it.candidate.releaseId == releaseId
                }
            }
        val targetResume = continuity.targetResumeReleaseId?.let { releaseId ->
            matchIncumbent(ranked, IncumbentKind.TARGET_RESUME_RELEASE) {
                it.candidate.releaseId == releaseId
            }
        }
        val trustedSourceGroup = continuity.committedSourceGroupKey?.let { group ->
            matchIncumbent(ranked, IncumbentKind.TRUSTED_SOURCE_GROUP) {
                it.candidate.sourceGroupKey == group
            }
        }
        val committedSource = continuity.committedSourceId?.let { sourceId ->
            matchIncumbent(ranked, IncumbentKind.COMMITTED_SOURCE) {
                it.candidate.sourceId == sourceId
            }
        }
        return sameTargetCommitted
            ?: targetResume
            ?: trustedSourceGroup
            ?: committedSource
            ?: (null to IncumbentKind.NONE)
    }

    private inline fun matchIncumbent(
        ranked: List<EvaluatedCandidate>,
        kind: IncumbentKind,
        predicate: (EvaluatedCandidate) -> Boolean,
    ): Pair<EvaluatedCandidate, IncumbentKind>? = ranked.firstOrNull(predicate)?.let { it to kind }

    private fun hasUnavailableExactIncumbent(
        snapshot: ReaderRoutingSnapshot,
        ranked: List<EvaluatedCandidate>,
    ): Boolean {
        val eligibleIds = ranked.mapTo(hashSetOf()) { it.candidate.releaseId }
        val continuity = snapshot.continuity
        val expected = if (
            continuity.committedChapterId == snapshot.targetChapterId &&
            continuity.committedReleaseId != null
        ) {
            continuity.committedReleaseId
        } else {
            continuity.targetResumeReleaseId
        }
        return expected != null && expected !in eligibleIds
    }

    private fun isDegradedRemote(candidate: EvaluatedCandidate): Boolean =
        candidate.preferredAccessMode == AccessMode.REMOTE &&
            ((candidate.remoteReliability?.value ?: BasisPoints.MAX_VALUE) < DEGRADED_RELIABILITY ||
                candidate.remoteCircuitState == CircuitState.HALF_OPEN)

    private companion object {
        const val DEGRADED_RELIABILITY = 8_500
    }
}
