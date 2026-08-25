package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.engine.internal.ContinuityArbiter
import app.openstory.reader.engine.internal.EvaluatedCandidate
import kotlin.test.Test
import kotlin.test.assertEquals

class ContinuityArbiterTest {
    private val arbiter = ContinuityArbiter()

    @Test
    fun sameTargetCommittedEligibleReleaseBeatsConflictingResumeForIncumbentResolution() {
        val committed = evaluated("committed", "s1", 7_000)
        val resume = evaluated("resume", "s2", 9_000)
        val result = arbiter.choose(
            ranked = listOf(resume, committed),
            snapshot = snapshot(
                continuity = ReadingContinuity(
                    committedChapterId = CanonicalChapterId("chapter"),
                    committedReleaseId = committed.candidate.releaseId,
                    committedSourceId = committed.candidate.sourceId,
                    targetResumeReleaseId = resume.candidate.releaseId,
                ),
            ),
            policy = ReaderRoutingPolicy.v1(),
        )
        assertEquals(committed.candidate.releaseId, result.incumbent?.candidate?.releaseId)
        assertEquals(IncumbentKind.SAME_TARGET_COMMITTED_RELEASE, result.incumbentKind)
    }

    @Test
    fun resumeEligibleBeatsCommittedSourceIncumbentOnCrossChapterTarget() {
        val resume = evaluated("resume", "other", 8_000)
        val sameSource = evaluated("same-source", "committed-source", 9_000)
        val result = arbiter.choose(
            ranked = listOf(sameSource, resume),
            snapshot = snapshot(
                continuity = ReadingContinuity(
                    committedChapterId = CanonicalChapterId("old-chapter"),
                    committedSourceId = PluginId("committed-source"),
                    targetResumeReleaseId = resume.candidate.releaseId,
                ),
            ),
            policy = ReaderRoutingPolicy.v1(),
        )
        assertEquals(resume.candidate.releaseId, result.incumbent?.candidate?.releaseId)
        assertEquals(IncumbentKind.TARGET_RESUME_RELEASE, result.incumbentKind)
    }

    @Test
    fun normalThresholdRetainsAt799AndSwitchesAt800() {
        val incumbent = evaluated("incumbent", "s1", 5_000)
        fun decision(challengerScore: Int) = arbiter.choose(
            ranked = listOf(evaluated("challenger", "s2", challengerScore), incumbent),
            snapshot = snapshot(continuity = ReadingContinuity(targetResumeReleaseId = incumbent.candidate.releaseId)),
            policy = ReaderRoutingPolicy.v1(),
        )
        assertEquals("incumbent", decision(5_799).winner?.candidate?.releaseId?.value)
        assertEquals(DecisionReason.TARGET_RESUME_INCUMBENT_RETAINED, decision(5_799).reason)
        assertEquals("challenger", decision(5_800).winner?.candidate?.releaseId?.value)
        assertEquals(DecisionReason.CHALLENGER_EXCEEDED_SWITCH_THRESHOLD, decision(5_800).reason)
    }

    @Test
    fun degradedRemoteThresholdRetainsAt349AndSwitchesAt350() {
        val incumbent = evaluated(
            "incumbent",
            "s1",
            5_000,
            mode = AccessMode.REMOTE,
            reliability = 8_000,
        )
        fun decision(challengerScore: Int) = arbiter.choose(
            ranked = listOf(evaluated("challenger", "s2", challengerScore), incumbent),
            snapshot = snapshot(continuity = ReadingContinuity(targetResumeReleaseId = incumbent.candidate.releaseId)),
            policy = ReaderRoutingPolicy.v1(),
        )
        assertEquals("incumbent", decision(5_349).winner?.candidate?.releaseId?.value)
        assertEquals("challenger", decision(5_350).winner?.candidate?.releaseId?.value)
    }

    @Test
    fun localPreferredIncumbentIsNotDegradedByRemoteFacts() {
        val incumbent = evaluated(
            "incumbent",
            "s1",
            5_000,
            mode = AccessMode.LOCAL,
            reliability = 1_000,
            circuit = CircuitState.HALF_OPEN,
        )
        val result = arbiter.choose(
            ranked = listOf(evaluated("challenger", "s2", 5_500), incumbent),
            snapshot = snapshot(continuity = ReadingContinuity(targetResumeReleaseId = incumbent.candidate.releaseId)),
            policy = ReaderRoutingPolicy.v1(),
        )
        assertEquals("incumbent", result.winner?.candidate?.releaseId?.value)
        assertEquals(BasisPoints(800), result.requiredThreshold)
    }

    @Test
    fun explicitEligibleReleaseBypassesHysteresis() {
        val explicit = evaluated("explicit", "s1", 1_000)
        val best = evaluated("best", "s2", 9_000)
        val result = arbiter.choose(
            ranked = listOf(best, explicit),
            snapshot = snapshot(explicit = explicit.candidate.releaseId),
            policy = ReaderRoutingPolicy.v1(),
        )
        assertEquals(explicit.candidate.releaseId, result.winner?.candidate?.releaseId)
        assertEquals(DecisionReason.EXPLICIT_ELIGIBLE_RELEASE, result.reason)
    }

    @Test
    fun unavailableResumeSwitchesImmediately() {
        val winner = evaluated("winner", "s2", 5_000)
        val result = arbiter.choose(
            ranked = listOf(winner),
            snapshot = snapshot(continuity = ReadingContinuity(targetResumeReleaseId = ChapterReleaseId("missing"))),
            policy = ReaderRoutingPolicy.v1(),
        )
        assertEquals(winner.candidate.releaseId, result.winner?.candidate?.releaseId)
        assertEquals(DecisionReason.INCUMBENT_UNAVAILABLE, result.reason)
    }

    private fun evaluated(
        release: String,
        source: String,
        score: Int,
        mode: AccessMode = AccessMode.REMOTE,
        reliability: Int = 10_000,
        circuit: CircuitState = CircuitState.CLOSED,
    ): EvaluatedCandidate {
        val candidate = RoutingCandidate(
            releaseId = ChapterReleaseId(release),
            sourceId = PluginId(source),
            languageTag = "vi",
            sourceGroupKey = null,
            publishedAtEpochMillis = null,
            completeness = BasisPoints(10_000),
            remoteAccess = CandidateRemoteAccess.PERMITTED,
            localAccess = if (mode == AccessMode.LOCAL) CandidateLocalAccess.AvailableExact("fp-$release") else CandidateLocalAccess.Miss,
        )
        return EvaluatedCandidate(
            candidate = candidate,
            localFingerprint = (candidate.localAccess as? CandidateLocalAccess.AvailableExact)?.fingerprint,
            remoteEligible = true,
            preferredAccessMode = mode,
            semanticFeatures = SemanticFeatureVector(BasisPoints(10_000), BasisPoints(0), BasisPoints(10_000), BasisPoints(5_000)),
            preferredAccessFeatures = AccessFeatureVector(BasisPoints(10_000), BasisPoints(reliability), BasisPoints(5_000), BasisPoints(0)),
            weightedScore = BasisPoints(score),
            remoteAccessScore = BasisPoints(9_000),
            remoteReliability = BasisPoints(reliability),
            remoteCircuitState = circuit,
        )
    }

    private fun snapshot(
        continuity: ReadingContinuity = ReadingContinuity(),
        explicit: ChapterReleaseId? = null,
    ) = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter"),
        chapterGraphRevision = ReaderChapterGraphRevision(1),
        planRevision = ReaderPlanRevision(0),
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = emptyList(),
        sourceHealth = emptyList(),
        continuity = continuity,
        networkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId = explicit,
        nowEpochMillis = 1L,
    )
}
