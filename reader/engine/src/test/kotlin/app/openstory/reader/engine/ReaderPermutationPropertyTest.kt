package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReaderPermutationPropertyTest {
    private val engine = ReaderRouteEngine.v1()

    @Test
    fun oneThousandSeededRoutingInputsReplayExactlyAndPreserveRouteInvariants() {
        repeat(1_000) { fixtureIndex ->
            val random = Random(SEED + fixtureIndex)
            val candidates = candidates(random, fixtureIndex)
            val policy = ReaderRoutingPolicy.v1(
                languageOrder = listOf("vi", "en", "ja", "fr").shuffled(random),
            )
            val explicit = candidates
                .filter { it.remoteAccess == CandidateRemoteAccess.PERMITTED || it.localAccess.isUsable() }
                .randomOrNull(random)
                ?.releaseId
            val baseSnapshot = snapshot(candidates, explicitReleaseId = explicit)
            val expected = engine.plan(baseSnapshot, policy)
            val replay = engine.plan(baseSnapshot, policy)
            val shuffled = engine.plan(snapshot(candidates.shuffled(random), explicitReleaseId = explicit), policy)

            assertEquals(expected, replay, "replay mismatch at fixture $fixtureIndex")
            assertEquals(expected, shuffled, "permutation mismatch at fixture $fixtureIndex")
            assertDecisionInvariants(expected, candidates, explicit, fixtureIndex)

            if (fixtureIndex % 25 == 0) {
                val offline = engine.plan(
                    snapshot(candidates.shuffled(random), ReaderNetworkClass.OFFLINE, explicit),
                    policy,
                )
                assertFalse(
                    offline.trace.routeConstruction.any { it.accessMode == AccessMode.REMOTE },
                    "OFFLINE emitted REMOTE at fixture $fixtureIndex",
                )
            }
        }
    }

    @Test
    fun explicitEligibleReleaseIsAlwaysSemanticWinner() {
        repeat(250) { fixtureIndex ->
            val random = Random(SEED xor fixtureIndex)
            val candidates = candidates(random, fixtureIndex).toMutableList()
            val explicit = candidate(
                id = "explicit-$fixtureIndex",
                source = "explicit-source-$fixtureIndex",
                language = "zz",
                completeness = 0,
                publishedAt = 0L,
                remoteAccess = CandidateRemoteAccess.PERMITTED,
                localAccess = CandidateLocalAccess.Miss,
            )
            candidates += explicit

            val decision = engine.plan(
                snapshot(candidates.shuffled(random), explicitReleaseId = explicit.releaseId),
                ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en")),
            )

            assertEquals(explicit.releaseId, decision.competitiveSet.primary?.releaseId)
            assertEquals(DecisionReason.EXPLICIT_ELIGIBLE_RELEASE, decision.reason)
        }
    }

    @Test
    fun exactScoreTiesResolveBySourceThenReleaseId() {
        val policy = ReaderRoutingPolicy.v1()
        val bySource = listOf(
            candidate("release-a", "source-z"),
            candidate("release-z", "source-a"),
        )
        val sourceTie = engine.plan(snapshot(bySource), policy)
        assertEquals(ChapterReleaseId("release-z"), sourceTie.competitiveSet.primary?.releaseId)

        val byRelease = listOf(
            candidate("release-z", "source-a"),
            candidate("release-a", "source-a"),
        )
        val releaseTie = engine.plan(snapshot(byRelease), policy)
        assertEquals(ChapterReleaseId("release-a"), releaseTie.competitiveSet.primary?.releaseId)
    }

    @Test
    fun openRemotePathNeverRoutesWhileExactLocalPathStillMayRoute() {
        val sourceId = PluginId("source-open")
        val candidate = candidate(
            "release-local",
            sourceId.value,
            localAccess = CandidateLocalAccess.AvailableExact("fp-local"),
        )
        val decision = engine.plan(
            ReaderRoutingSnapshot.create(
                targetChapterId = CanonicalChapterId("chapter"),
                chapterGraphRevision = ReaderChapterGraphRevision(1),
                planRevision = ReaderPlanRevision(1),
                routingIntent = RoutingIntent.FOREGROUND,
                candidates = listOf(candidate),
                sourceHealth = listOf(
                    SourceHealthSnapshot(
                        key = SourceOperationKey(sourceId),
                        state = SourceHealthState(
                            circuitState = CircuitState.OPEN,
                            openCount = 1,
                            openedAtEpochMillis = 1L,
                            nextProbeAtEpochMillis = 31_001L,
                        ),
                        origin = SourceHealthOrigin.PROCESS_OBSERVED,
                    ),
                ),
                continuity = ReadingContinuity(),
                networkClass = ReaderNetworkClass.UNKNOWN,
                explicitReleaseId = null,
                nowEpochMillis = 1L,
            ),
            ReaderRoutingPolicy.v1(),
        )

        val primary = assertNotNull(decision.competitiveSet.primary)
        assertEquals(AccessMode.LOCAL, primary.accessMode)
        assertEquals("fp-local", primary.localFingerprint)
        assertFalse(decision.trace.routeConstruction.any { it.accessMode == AccessMode.REMOTE })
    }

    private fun assertDecisionInvariants(
        decision: ReaderRouteDecision,
        candidates: List<RoutingCandidate>,
        explicitReleaseId: ChapterReleaseId?,
        fixtureIndex: Int,
    ) {
        val attempts = decision.trace.routeConstruction
        val unusable = candidates.filter {
            it.remoteAccess == CandidateRemoteAccess.SOURCE_UNAVAILABLE && !it.localAccess.isUsable()
        }.mapTo(hashSetOf()) { it.releaseId }

        assertFalse(
            decision.competitiveSet.primary?.releaseId?.let { it in unusable } == true,
            "unusable candidate won fixture $fixtureIndex",
        )
        attempts.filter { it.accessMode == AccessMode.LOCAL }.forEach { attempt ->
            assertTrue(!attempt.localFingerprint.isNullOrBlank(), "LOCAL locator missing at fixture $fixtureIndex")
        }
        assertEquals(attempts.size, attempts.map { it.attemptId }.distinct().size)
        assertTrue(attempts.count { it.accessMode == AccessMode.REMOTE } <= 4)
        decision.trace.candidateEvaluations.forEach { evaluation ->
            evaluation.semanticWeightedScore?.let { assertTrue(it.value in 0..10_000) }
            evaluation.remoteAccessScore?.let { assertTrue(it.value in 0..10_000) }
        }
        if (explicitReleaseId != null && explicitReleaseId !in unusable) {
            assertEquals(explicitReleaseId, decision.competitiveSet.primary?.releaseId)
        }
    }

    private fun candidates(random: Random, fixtureIndex: Int): List<RoutingCandidate> {
        val count = random.nextInt(1, 25)
        val languages = listOf("vi", "en", "ja", "fr", "de")
        return List(count) { index ->
            val local = when (random.nextInt(5)) {
                0 -> CandidateLocalAccess.AvailableExact("fp-$fixtureIndex-$index")
                1 -> CandidateLocalAccess.AvailableUnverified("uv-$fixtureIndex-$index")
                2 -> CandidateLocalAccess.KnownInvalid("bad-$fixtureIndex-$index")
                else -> CandidateLocalAccess.Miss
            }
            candidate(
                id = "release-$fixtureIndex-$index",
                source = "source-${random.nextInt(8)}",
                language = languages[random.nextInt(languages.size)],
                completeness = random.nextInt(0, 10_001),
                publishedAt = if (random.nextInt(6) == 0) null else random.nextLong(0L, 10_000_000L),
                remoteAccess = if (random.nextInt(5) == 0) {
                    CandidateRemoteAccess.SOURCE_UNAVAILABLE
                } else {
                    CandidateRemoteAccess.PERMITTED
                },
                localAccess = local,
            )
        }
    }

    private fun snapshot(
        candidates: List<RoutingCandidate>,
        networkClass: ReaderNetworkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId: ChapterReleaseId? = null,
    ): ReaderRoutingSnapshot = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter"),
        chapterGraphRevision = ReaderChapterGraphRevision(7),
        planRevision = ReaderPlanRevision(11),
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = candidates,
        sourceHealth = emptyList(),
        continuity = ReadingContinuity(),
        networkClass = networkClass,
        explicitReleaseId = explicitReleaseId,
        nowEpochMillis = 50_000L,
    )

    private fun candidate(
        id: String,
        source: String,
        language: String = "vi",
        completeness: Int = 10_000,
        publishedAt: Long? = 1_000L,
        remoteAccess: CandidateRemoteAccess = CandidateRemoteAccess.PERMITTED,
        localAccess: CandidateLocalAccess = CandidateLocalAccess.Miss,
    ): RoutingCandidate = RoutingCandidate(
        releaseId = ChapterReleaseId(id),
        sourceId = PluginId(source),
        languageTag = language,
        sourceGroupKey = null,
        publishedAtEpochMillis = publishedAt,
        completeness = BasisPoints(completeness),
        remoteAccess = remoteAccess,
        localAccess = localAccess,
    )

    private fun CandidateLocalAccess.isUsable(): Boolean = when (this) {
        is CandidateLocalAccess.AvailableExact,
        is CandidateLocalAccess.AvailableUnverified,
        -> true
        CandidateLocalAccess.Miss,
        CandidateLocalAccess.Unknown,
        is CandidateLocalAccess.KnownInvalid,
        -> false
    }

    private companion object {
        const val SEED = 0x48455331
    }
}
