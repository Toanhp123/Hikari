package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReaderDecisionTraceTest {
    @Test
    fun traceUsesCanonicalCandidateOrderAndSnapshotRevisions() {
        val snapshot = snapshot(
            sourceHealth = listOf(
                health("z-source", SourceHealthOrigin.PROCESS_OBSERVED),
                health("a-source", SourceHealthOrigin.STARTUP_NEUTRAL),
            ),
        )
        val decision = ReaderRouteEngine.v1().plan(snapshot, ReaderRoutingPolicy.v1())

        assertEquals(snapshot.planRevision, decision.planRevision)
        assertEquals(snapshot.planRevision, decision.trace.planRevision)
        assertEquals(snapshot.chapterGraphRevision, decision.trace.chapterGraphRevision)
        assertEquals(
            listOf(ChapterReleaseId("a-release"), ChapterReleaseId("z-release")),
            decision.trace.canonicalCandidateIds,
        )
        assertEquals(
            listOf(PluginId("a-source"), PluginId("z-source")),
            decision.trace.healthOrigins.map(HealthOriginTrace::sourceId),
        )
    }

    @Test
    fun healthOriginChangesTraceWithoutChangingCompatibilityDecision() {
        val startup = ReaderRouteEngine.v1().plan(
            snapshot(sourceHealth = listOf(health("a-source", SourceHealthOrigin.STARTUP_NEUTRAL))),
            ReaderRoutingPolicy.v1(),
        )
        val observed = ReaderRouteEngine.v1().plan(
            snapshot(sourceHealth = listOf(health("a-source", SourceHealthOrigin.PROCESS_OBSERVED))),
            ReaderRoutingPolicy.v1(),
        )

        assertEquals(startup.competitiveSet, observed.competitiveSet)
        assertEquals(startup.recoveryChain, observed.recoveryChain)
        assertEquals(startup.reason, observed.reason)
        assertNotEquals(startup.trace.healthOrigins, observed.trace.healthOrigins)
    }

    @Test
    fun decisionReasonRejectionCodeAndDiagnosticNoteStayDistinctTypes() {
        val decisionReason: DecisionReason = DecisionReason.TOP_RANKED_NO_INCUMBENT
        val rejectionCode: RejectionCode = RejectionCode.NO_USABLE_ACCESS_PATH
        val diagnostic = DiagnosticNote(RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT)

        assertEquals(DecisionReason.TOP_RANKED_NO_INCUMBENT, decisionReason)
        assertEquals(RejectionCode.NO_USABLE_ACCESS_PATH, rejectionCode)
        assertEquals(RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT, diagnostic.code)
    }

    private fun snapshot(sourceHealth: List<SourceHealthSnapshot>) = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter"),
        chapterGraphRevision = ReaderChapterGraphRevision(8),
        planRevision = ReaderPlanRevision(3),
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = listOf(
            candidate("z-release", "z-source"),
            candidate("a-release", "a-source"),
        ),
        sourceHealth = sourceHealth,
        continuity = ReadingContinuity(),
        networkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId = null,
        nowEpochMillis = 100L,
    )

    private fun candidate(release: String, source: String) = RoutingCandidate(
        releaseId = ChapterReleaseId(release),
        sourceId = PluginId(source),
        languageTag = "en",
        sourceGroupKey = null,
        publishedAtEpochMillis = 1L,
        completeness = BasisPoints(10_000),
        remoteAccess = CandidateRemoteAccess.PERMITTED,
        localAccess = CandidateLocalAccess.Unknown,
    )

    private fun health(source: String, origin: SourceHealthOrigin) = SourceHealthSnapshot(
        key = SourceOperationKey(PluginId(source)),
        state = SourceHealthState(),
        origin = origin,
    )
}
