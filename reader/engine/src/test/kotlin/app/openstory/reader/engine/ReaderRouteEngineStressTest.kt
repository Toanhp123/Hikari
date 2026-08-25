package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRouteEngineStressTest {
    private val engine = ReaderRouteEngine.v1()

    @Test
    fun fiveHundredCandidatesAcrossFiftySourcesRemainDeterministicForOneThousandReplans() {
        val candidates = List(RELEASE_COUNT) { index ->
            RoutingCandidate(
                releaseId = ChapterReleaseId("release-${index.toString().padStart(3, '0')}"),
                sourceId = PluginId("source-${(index % SOURCE_COUNT).toString().padStart(2, '0')}"),
                languageTag = "lang-$index",
                sourceGroupKey = null,
                publishedAtEpochMillis = index.toLong(),
                completeness = BasisPoints(10_000 - (index % 1_001)),
                remoteAccess = CandidateRemoteAccess.PERMITTED,
                localAccess = if (index % 11 == 0) {
                    CandidateLocalAccess.AvailableExact("fp-$index")
                } else {
                    CandidateLocalAccess.Miss
                },
            )
        }
        val policy = ReaderRoutingPolicy.v1(
            languageOrder = List(RELEASE_COUNT) { "lang-$it" },
        )
        val expected = engine.plan(snapshot(candidates), policy)
        val random = Random(0x4D375354)

        repeat(REPLAN_COUNT) { iteration ->
            val input = if (iteration % 50 == 0) candidates.shuffled(random) else candidates
            val decision = engine.plan(snapshot(input), policy)
            assertEquals(expected, decision, "determinism mismatch at replan $iteration")
            assertScaleShape(decision, candidates.size)
        }
    }

    @Test
    fun languagePreferenceIndexPreservesOrderingWithoutPerCandidateLinearSearchContract() {
        val policy = ReaderRoutingPolicy.v1(
            languageOrder = List(RELEASE_COUNT) { "lang-$it" },
        )

        assertEquals(0, policy.languagePreferenceRank("lang-0"))
        assertEquals(249, policy.languagePreferenceRank("lang-249"))
        assertEquals(499, policy.languagePreferenceRank("lang-499"))
        assertEquals(null, policy.languagePreferenceRank("not-listed"))
        assertTrue(policy.isLanguageAllowed("lang-300"))
        assertFalse(policy.isLanguageAllowed("not-listed"))
    }

    @Test
    fun sourceHealthHistoryRemainsBoundedAtTwentySamplesUnderOneThousandObservations() {
        val reducer = SourceHealthReducer.v1()
        val policy = HealthPolicy.v1(maxLatencySamples = 20)
        var state = SourceHealthState()

        repeat(1_000) { index ->
            state = reducer.reduce(
                previous = state,
                observation = SourceObservation.Success.Remote(
                    RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
                    latencyMillis = index.toLong(),
                ),
                nowEpochMillis = index.toLong(),
                policy = policy,
            )
            assertTrue(state.recentLatencySamplesMillis.size <= 20)
        }

        assertEquals((980L..999L).toList(), state.recentLatencySamplesMillis)
    }

    private fun assertScaleShape(decision: ReaderRouteDecision, candidateCount: Int) {
        val attempts = decision.trace.routeConstruction
        assertEquals(candidateCount, decision.trace.canonicalCandidateIds.size)
        assertEquals(candidateCount, decision.trace.candidateEvaluations.size)
        assertEquals(candidateCount, decision.trace.stableRanking.size)
        assertTrue(attempts.size <= 7)
        assertTrue(attempts.count { it.accessMode == AccessMode.REMOTE } <= 4)
        assertEquals(attempts.size, attempts.map { it.attemptId }.distinct().size)
        attempts.filter { it.accessMode == AccessMode.LOCAL }.forEach { attempt ->
            assertTrue(!attempt.localFingerprint.isNullOrBlank())
        }
        // HES trace is per-candidate plus bounded route output; it never materializes n*n candidate pairs.
        assertTrue(decision.trace.candidateEvaluations.size <= candidateCount)
    }

    private fun snapshot(candidates: List<RoutingCandidate>): ReaderRoutingSnapshot = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter-stress"),
        chapterGraphRevision = ReaderChapterGraphRevision(9),
        planRevision = ReaderPlanRevision(13),
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = candidates,
        sourceHealth = emptyList(),
        continuity = ReadingContinuity(),
        networkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId = null,
        nowEpochMillis = 100_000L,
    )

    private companion object {
        const val SOURCE_COUNT = 50
        const val RELEASE_COUNT = 500
        const val REPLAN_COUNT = 1_000
    }
}
