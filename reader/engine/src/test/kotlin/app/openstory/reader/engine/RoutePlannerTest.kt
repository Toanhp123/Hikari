package app.openstory.reader.engine

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.engine.internal.EvaluatedCandidate
import app.openstory.reader.engine.internal.RoutePlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutePlannerTest {
    private val planner = RoutePlanner()

    @Test
    fun winnerExactLocalRoutesLocalThenSameWinnerRemoteBeforeNextCandidate() {
        val winner = evaluated("winner", "s1", local = "fp", remote = true)
        val next = evaluated("next", "s2", local = null, remote = true)
        val plan = planner.plan(listOf(winner, next), winner, ReaderRoutingPolicy.v1())
        val attempts = plan.attempts

        assertEquals(
            listOf(
                Triple("winner", AccessMode.LOCAL, "fp"),
                Triple("winner", AccessMode.REMOTE, null),
                Triple("next", AccessMode.REMOTE, null),
            ),
            attempts.map { Triple(it.releaseId.value, it.accessMode, it.localFingerprint) },
        )
        assertEquals(listOf("attempt-0", "attempt-1", "attempt-2"), attempts.map { it.attemptId })
        assertEquals(AttemptRole.PRIMARY, attempts.first().role)
        assertTrue(attempts.drop(1).all { it.role == AttemptRole.FALLBACK })
    }

    @Test
    fun remainingCandidatesFollowStableRankAndLocalBeforeRemote() {
        val a = evaluated("a", "s1", local = null, remote = true)
        val b = evaluated("b", "s2", local = "fp-b", remote = true)
        val c = evaluated("c", "s3", local = "fp-c", remote = false)
        val plan = planner.plan(listOf(a, b, c), a, ReaderRoutingPolicy.v1())
        assertEquals(
            listOf("a:R", "b:L", "b:R", "c:L"),
            plan.attempts.map { "${it.releaseId.value}:${if (it.accessMode == AccessMode.LOCAL) "L" else "R"}" },
        )
    }

    @Test
    fun recoveryAndRemoteBudgetsAreCappedAndAttemptsStayUnique() {
        val candidates = (0..9).map { index -> evaluated("r$index", "s$index", local = null, remote = true) }
        val plan = planner.plan(candidates, candidates.first(), ReaderRoutingPolicy.v1())
        assertTrue(plan.attempts.size <= 7)
        assertTrue(plan.attempts.count { it.accessMode == AccessMode.REMOTE } <= 4)
        assertEquals(plan.attempts.size, plan.attempts.map { Triple(it.releaseId, it.accessMode, it.localFingerprint) }.distinct().size)
        assertEquals(plan.attempts.size, plan.attempts.map { it.attemptId }.distinct().size)
    }

    private fun evaluated(
        release: String,
        source: String,
        local: String?,
        remote: Boolean,
    ): EvaluatedCandidate {
        val candidate = RoutingCandidate(
            releaseId = ChapterReleaseId(release),
            sourceId = PluginId(source),
            languageTag = "vi",
            sourceGroupKey = null,
            publishedAtEpochMillis = null,
            completeness = BasisPoints(10_000),
            remoteAccess = CandidateRemoteAccess.PERMITTED,
            localAccess = local?.let(CandidateLocalAccess::AvailableExact) ?: CandidateLocalAccess.Miss,
        )
        return EvaluatedCandidate(
            candidate = candidate,
            localFingerprint = local,
            remoteEligible = remote,
            preferredAccessMode = if (local != null) AccessMode.LOCAL else AccessMode.REMOTE,
            semanticFeatures = SemanticFeatureVector(BasisPoints(10_000), BasisPoints(0), BasisPoints(10_000), BasisPoints(5_000)),
            preferredAccessFeatures = AccessFeatureVector(BasisPoints(10_000), BasisPoints(10_000), BasisPoints(5_000), BasisPoints(if (local != null) 10_000 else 0)),
            weightedScore = BasisPoints(5_000),
            remoteAccessScore = BasisPoints(9_000).takeIf { remote },
            remoteReliability = BasisPoints(10_000).takeIf { remote },
            remoteCircuitState = CircuitState.CLOSED.takeIf { remote },
        )
    }
}
