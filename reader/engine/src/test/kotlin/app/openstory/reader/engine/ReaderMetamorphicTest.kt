package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderMetamorphicTest {
    private val engine = ReaderRouteEngine.v1()

    @Test
    fun addingDisabledCandidateCannotChangePriorEligibleWinner() {
        val base = listOf(
            candidate("winner", "source-a", language = "vi"),
            candidate("runner", "source-b", language = "en"),
        )
        val policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en"))
        val expected = engine.plan(snapshot(base), policy)
        val withDisabled = engine.plan(
            snapshot(
                base + candidate(
                    "disabled",
                    "source-disabled",
                    language = "vi",
                    remoteAccess = CandidateRemoteAccess.SOURCE_UNAVAILABLE,
                ),
            ),
            policy,
        )

        assertEquals(expected.competitiveSet.primary?.releaseId, withDisabled.competitiveSet.primary?.releaseId)
        assertEquals(expected.trace.stableRanking, withDisabled.trace.stableRanking)
    }

    @Test
    fun improvingWinnerReliabilityAloneCannotMakeItLose() {
        val candidates = listOf(
            candidate("winner", "source-a", language = "vi"),
            candidate("runner", "source-b", language = "en"),
        )
        val policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en"))
        val baseline = engine.plan(
            snapshot(candidates, health = listOf(health("source-a", 8_500), health("source-b", 8_500))),
            policy,
        )
        val improved = engine.plan(
            snapshot(candidates, health = listOf(health("source-a", 10_000), health("source-b", 8_500))),
            policy,
        )

        assertEquals(ChapterReleaseId("winner"), baseline.competitiveSet.primary?.releaseId)
        assertEquals(baseline.competitiveSet.primary?.releaseId, improved.competitiveSet.primary?.releaseId)
    }

    @Test
    fun permutingRejectedCandidatesCannotAlterEligibleRanking() {
        val eligible = listOf(candidate("winner", "source-a"), candidate("runner", "source-b"))
        val rejected = listOf(
            candidate("rejected-c", "source-c", remoteAccess = CandidateRemoteAccess.SOURCE_UNAVAILABLE),
            candidate("rejected-d", "source-d", remoteAccess = CandidateRemoteAccess.SOURCE_UNAVAILABLE),
            candidate("rejected-e", "source-e", remoteAccess = CandidateRemoteAccess.SOURCE_UNAVAILABLE),
        )
        val policy = ReaderRoutingPolicy.v1()

        val first = engine.plan(snapshot(eligible + rejected), policy)
        val second = engine.plan(snapshot(eligible + rejected.reversed()), policy)

        assertEquals(first.competitiveSet.primary?.releaseId, second.competitiveSet.primary?.releaseId)
        assertEquals(first.trace.stableRanking, second.trace.stableRanking)
    }

    @Test
    fun challengerImprovementBelowHysteresisThresholdCannotSwitchIncumbent() {
        val incumbent = candidate("incumbent", "source-a", language = "en")
        val challenger = candidate("challenger", "source-b", language = "vi")
        val policy = ReaderRoutingPolicy.v1(
            languageOrder = listOf("vi", "en"),
            normalSwitchThreshold = BasisPoints(800),
        )
        val continuity = ReadingContinuity(committedSourceId = PluginId("source-a"))

        val decision = engine.plan(snapshot(listOf(incumbent, challenger), continuity = continuity), policy)

        assertEquals(incumbent.releaseId, decision.competitiveSet.primary?.releaseId)
        assertEquals(BasisPoints(800), decision.trace.requiredHysteresisThreshold)
        assertTrue((decision.trace.switchAdvantage?.value ?: 10_000) < 800)
    }

    @Test
    fun removingUnavailableRemotePathCannotRecreateIt() {
        val local = candidate(
            "release",
            "source-a",
            localAccess = CandidateLocalAccess.AvailableExact("fp"),
        )
        val withRemote = engine.plan(snapshot(listOf(local)), ReaderRoutingPolicy.v1())
        assertTrue(withRemote.trace.routeConstruction.any { it.accessMode == AccessMode.REMOTE })

        val withoutRemote = engine.plan(
            snapshot(listOf(local.copy(remoteAccess = CandidateRemoteAccess.SOURCE_UNAVAILABLE))),
            ReaderRoutingPolicy.v1(),
        )
        assertEquals(AccessMode.LOCAL, withoutRemote.competitiveSet.primary?.accessMode)
        assertFalse(withoutRemote.trace.routeConstruction.any { it.accessMode == AccessMode.REMOTE })
    }

    @Test
    fun unrelatedResumeFingerprintCannotTurnLocalMissIntoKnownInvalid() {
        val candidate = candidate("release", "source-a", localAccess = CandidateLocalAccess.Miss)
        val withoutFingerprint = engine.plan(
            snapshot(
                listOf(candidate),
                continuity = ReadingContinuity(targetResumeReleaseId = candidate.releaseId),
            ),
            ReaderRoutingPolicy.v1(),
        )
        val withHistoricalFingerprint = engine.plan(
            snapshot(
                listOf(candidate),
                continuity = ReadingContinuity(
                    targetResumeReleaseId = candidate.releaseId,
                    targetResumeFingerprint = "old-unrelated-fingerprint",
                ),
            ),
            ReaderRoutingPolicy.v1(),
        )

        assertEquals(withoutFingerprint.competitiveSet, withHistoricalFingerprint.competitiveSet)
        assertEquals(withoutFingerprint.recoveryChain, withHistoricalFingerprint.recoveryChain)
        assertFalse(withHistoricalFingerprint.rejections.any { it.code == RejectionCode.LOCAL_COPY_KNOWN_INVALID })
    }

    private fun snapshot(
        candidates: List<RoutingCandidate>,
        continuity: ReadingContinuity = ReadingContinuity(),
        health: List<SourceHealthSnapshot> = emptyList(),
    ): ReaderRoutingSnapshot = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter"),
        chapterGraphRevision = ReaderChapterGraphRevision(1),
        planRevision = ReaderPlanRevision(1),
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = candidates,
        sourceHealth = health,
        continuity = continuity,
        networkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId = null,
        nowEpochMillis = 1_000L,
    )

    private fun candidate(
        id: String,
        source: String,
        language: String = "vi",
        remoteAccess: CandidateRemoteAccess = CandidateRemoteAccess.PERMITTED,
        localAccess: CandidateLocalAccess = CandidateLocalAccess.Miss,
    ): RoutingCandidate = RoutingCandidate(
        releaseId = ChapterReleaseId(id),
        sourceId = PluginId(source),
        languageTag = language,
        sourceGroupKey = null,
        publishedAtEpochMillis = 1_000L,
        completeness = BasisPoints(10_000),
        remoteAccess = remoteAccess,
        localAccess = localAccess,
    )

    private fun health(source: String, reliability: Int): SourceHealthSnapshot = SourceHealthSnapshot(
        key = SourceOperationKey(PluginId(source)),
        state = SourceHealthState(successEwmaBasisPoints = BasisPoints(reliability)),
        origin = SourceHealthOrigin.PROCESS_OBSERVED,
    )
}
