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
        if (ranked.isEmpty()) {
            return ArbitrationResult(
                winner = null,
                reason = DecisionReason.NO_ELIGIBLE_CANDIDATE,
                incumbent = null,
                incumbentKind = IncumbentKind.NONE,
                rawChallenger = null,
                switchAdvantage = null,
                requiredThreshold = null,
            )
        }

        snapshot.explicitReleaseId?.let { explicitId ->
            ranked.firstOrNull { it.candidate.releaseId == explicitId }?.let { explicit ->
                return ArbitrationResult(
                    winner = explicit,
                    reason = DecisionReason.EXPLICIT_ELIGIBLE_RELEASE,
                    incumbent = null,
                    incumbentKind = IncumbentKind.NONE,
                    rawChallenger = ranked.first(),
                    switchAdvantage = null,
                    requiredThreshold = null,
                )
            }
        }

        val (incumbent, kind) = resolveIncumbent(ranked, snapshot)
        val raw = ranked.first()
        if (incumbent == null) {
            return ArbitrationResult(
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
        }

        val threshold = if (isDegradedRemote(incumbent)) {
            policy.degradedSwitchThreshold
        } else {
            policy.normalSwitchThreshold
        }
        val advantage = (raw.weightedScore.value - incumbent.weightedScore.value).coerceIn(0, 10_000)
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
        if (continuity.committedChapterId == snapshot.targetChapterId && continuity.committedReleaseId != null) {
            ranked.firstOrNull { it.candidate.releaseId == continuity.committedReleaseId }?.let {
                return it to IncumbentKind.SAME_TARGET_COMMITTED_RELEASE
            }
        }
        continuity.targetResumeReleaseId?.let { releaseId ->
            ranked.firstOrNull { it.candidate.releaseId == releaseId }?.let {
                return it to IncumbentKind.TARGET_RESUME_RELEASE
            }
        }
        continuity.committedSourceGroupKey?.let { group ->
            ranked.firstOrNull { it.candidate.sourceGroupKey == group }?.let {
                return it to IncumbentKind.TRUSTED_SOURCE_GROUP
            }
        }
        continuity.committedSourceId?.let { sourceId ->
            ranked.firstOrNull { it.candidate.sourceId == sourceId }?.let {
                return it to IncumbentKind.COMMITTED_SOURCE
            }
        }
        return null to IncumbentKind.NONE
    }

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
            ((candidate.remoteReliability?.value ?: 10_000) < DEGRADED_RELIABILITY ||
                candidate.remoteCircuitState == CircuitState.HALF_OPEN)

    private companion object {
        const val DEGRADED_RELIABILITY = 8_500
    }
}
