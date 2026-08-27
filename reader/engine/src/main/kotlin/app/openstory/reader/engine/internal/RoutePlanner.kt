package app.openstory.reader.engine.internal

import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.HedgeDirective
import app.openstory.reader.engine.HedgeOmissionReason
import app.openstory.reader.engine.ReaderNetworkClass
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.engine.RoutingIntent

internal data class PlannedRoute(
    val attempts: List<RouteAttempt>,
    val hedgeDirective: HedgeDirective,
)

/** Builds bounded deterministic LOCAL/REMOTE execution order with at most one foreground hedge. */
internal class RoutePlanner {
    fun plan(
        ranked: List<EvaluatedCandidate>,
        winner: EvaluatedCandidate?,
        policy: ReaderRoutingPolicy,
        snapshot: ReaderRoutingSnapshot? = null,
    ): PlannedRoute {
        if (winner == null) {
            return PlannedRoute(
                attempts = emptyList(),
                hedgeDirective = HedgeDirective.Omitted(HedgeOmissionReason.NOT_ELIGIBLE),
            )
        }
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
        val hedgeRaw = selectHedge(
            bounded = bounded,
            ranked = ranked,
            snapshot = snapshot,
            policy = policy,
        )
        val finalOrder = buildList {
            bounded.firstOrNull()?.let(::add)
            hedgeRaw?.let(::add)
            bounded.drop(1).forEach { if (it !== hedgeRaw) add(it) }
        }
        val attempts = finalOrder.mapIndexed { index, attempt ->
            RouteAttempt(
                attemptId = "attempt-$index",
                releaseId = attempt.candidate.candidate.releaseId,
                sourceId = attempt.candidate.candidate.sourceId,
                accessMode = attempt.accessMode,
                localFingerprint = attempt.fingerprint,
                role = when {
                    index == 0 -> AttemptRole.PRIMARY
                    attempt === hedgeRaw -> AttemptRole.HEDGE
                    else -> AttemptRole.FALLBACK
                },
            )
        }
        val hedgeAttempt = attempts.firstOrNull { it.role == AttemptRole.HEDGE }
        return PlannedRoute(
            attempts = attempts,
            hedgeDirective = hedgeAttempt?.let { HedgeDirective.Launch(it, policy.hedge.delayMillis) }
                ?: HedgeDirective.Omitted(HedgeOmissionReason.NOT_ELIGIBLE),
        )
    }

    private fun selectHedge(
        bounded: List<RawAttempt>,
        ranked: List<EvaluatedCandidate>,
        snapshot: ReaderRoutingSnapshot?,
        policy: ReaderRoutingPolicy,
    ): RawAttempt? {
        val primary = bounded.firstOrNull()
        val eligiblePrimary = primary?.takeIf { isHedgeEligiblePrimary(it, snapshot, policy) }
        return eligiblePrimary?.let { selectHedgeAlternate(bounded, ranked, it, policy) }
    }

    private fun isHedgeEligiblePrimary(
        primary: RawAttempt,
        snapshot: ReaderRoutingSnapshot?,
        policy: ReaderRoutingPolicy,
    ): Boolean {
        val eligibleSnapshot = snapshot?.takeIf {
            it.routingIntent == RoutingIntent.FOREGROUND && it.networkClass == ReaderNetworkClass.UNMETERED
        }
        val primaryHealth = if (eligibleSnapshot != null && primary.accessMode == AccessMode.REMOTE) {
            eligibleSnapshot.sourceHealth
                .firstOrNull { it.key.sourceId == primary.candidate.candidate.sourceId }
                ?.state
        } else {
            null
        }
        val primaryP95 = primaryHealth
            ?.takeIf { it.recentLatencySamplesMillis.size >= policy.hedge.minimumLatencySamples }
            ?.p95LatencyMillis
        return primaryP95 != null && primaryP95 >= policy.hedge.primaryP95ThresholdMillis
    }

    private fun selectHedgeAlternate(
        bounded: List<RawAttempt>,
        ranked: List<EvaluatedCandidate>,
        primary: RawAttempt,
        policy: ReaderRoutingPolicy,
    ): RawAttempt? {
        val rankIndex = ranked.mapIndexed { index, candidate -> candidate.candidate.releaseId to index }.toMap()
        return bounded
            .asSequence()
            .drop(1)
            .filter { it.accessMode == AccessMode.REMOTE }
            .filter { it.candidate.candidate.sourceId != primary.candidate.candidate.sourceId }
            .filter {
                val score = it.candidate.remoteAccessScore
                score != null && score.value >= policy.hedge.alternateMinimumRemoteAccessScore.value
            }
            .filter {
                val reliability = it.candidate.remoteReliability
                reliability != null && reliability.value >= policy.hedge.alternateMinimumReliability.value
            }
            .minWithOrNull(
                compareBy<RawAttempt>(
                    { rankIndex.getValue(it.candidate.candidate.releaseId) },
                    { it.candidate.candidate.sourceId.value },
                    { it.candidate.candidate.releaseId.value },
                ),
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
