package app.openstory.reader.engine.internal

import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.RouteAttempt

internal data class PlannedRoute(
    val attempts: List<RouteAttempt>,
)

/** Builds bounded deterministic LOCAL/REMOTE execution order. Hedging remains disabled in M4. */
internal class RoutePlanner {
    fun plan(
        ranked: List<EvaluatedCandidate>,
        winner: EvaluatedCandidate?,
        policy: ReaderRoutingPolicy,
    ): PlannedRoute {
        if (winner == null) return PlannedRoute(emptyList())
        val semanticOrder = buildList {
            add(winner)
            ranked.forEach { if (it.candidate.releaseId != winner.candidate.releaseId) add(it) }
        }

        val raw = mutableListOf<RawAttempt>()
        var remoteCount = 0
        semanticOrder.forEach { candidate ->
            candidate.localFingerprint?.let { fingerprint ->
                raw += RawAttempt(candidate, AccessMode.LOCAL, fingerprint)
            }
            if (candidate.remoteEligible && remoteCount < policy.maxPlannedForegroundRemoteAttempts) {
                raw += RawAttempt(candidate, AccessMode.REMOTE, null)
                remoteCount += 1
            }
        }

        val maximumAttempts = 1 + policy.maxRecoveryAttempts
        val unique = linkedMapOf<AttemptKey, RawAttempt>()
        raw.forEach { attempt ->
            unique.putIfAbsent(
                AttemptKey(
                    releaseId = attempt.candidate.candidate.releaseId.value,
                    accessMode = attempt.accessMode,
                    fingerprint = attempt.fingerprint,
                ),
                attempt,
            )
        }
        val bounded = unique.values.take(maximumAttempts)
        return PlannedRoute(
            attempts = bounded.mapIndexed { index, attempt ->
                RouteAttempt(
                    attemptId = "attempt-$index",
                    releaseId = attempt.candidate.candidate.releaseId,
                    sourceId = attempt.candidate.candidate.sourceId,
                    accessMode = attempt.accessMode,
                    localFingerprint = attempt.fingerprint,
                    role = if (index == 0) AttemptRole.PRIMARY else AttemptRole.FALLBACK,
                )
            },
        )
    }

    private data class RawAttempt(
        val candidate: EvaluatedCandidate,
        val accessMode: AccessMode,
        val fingerprint: String?,
    )

    private data class AttemptKey(
        val releaseId: String,
        val accessMode: AccessMode,
        val fingerprint: String?,
    )
}
