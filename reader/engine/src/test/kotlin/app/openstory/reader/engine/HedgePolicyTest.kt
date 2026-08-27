package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HedgePolicyTest {
    private val engine = ReaderRouteEngine.v1()

    @Test
    fun `eligible remote foreground route plans one delayed hedge`() {
        val decision = decision()
        val launch = assertIs<HedgeDirective.Launch>(decision.hedgeDirective)

        assertEquals(650L, launch.delayMillis)
        assertEquals(AttemptRole.PRIMARY, decision.competitiveSet.primary?.role)
        assertEquals(AttemptRole.HEDGE, launch.attempt.role)
        assertEquals(PluginId("alternate-source"), launch.attempt.sourceId)
        assertEquals(launch.attempt, decision.competitiveSet.hedge)
        assertTrue(decision.recoveryChain.none { it.releaseId == launch.attempt.releaseId })
    }

    @Test
    fun `prefetch never hedges`() {
        assertHedgeOmitted(decision(intent = RoutingIntent.PREFETCH))
    }

    @Test
    fun `metered network never hedges`() {
        assertHedgeOmitted(decision(network = ReaderNetworkClass.METERED))
    }

    @Test
    fun `local primary never hedges`() {
        assertHedgeOmitted(decision(primaryLocalFingerprint = "primary-local"))
    }

    @Test
    fun `primary with fewer than three latency samples never hedges`() {
        assertHedgeOmitted(decision(primaryLatencies = listOf(1_300L, 1_300L)))
    }

    @Test
    fun `primary below p95 threshold never hedges`() {
        assertHedgeOmitted(decision(primaryLatencies = listOf(1_199L, 1_199L, 1_199L)))
    }

    @Test
    fun `alternate without remote access never hedges`() {
        assertHedgeOmitted(decision(alternateRemoteAccess = CandidateRemoteAccess.SOURCE_UNAVAILABLE))
    }

    @Test
    fun `alternate on primary source never hedges`() {
        assertHedgeOmitted(decision(alternateSource = "primary-source"))
    }

    @Test
    fun `alternate below remote access threshold never hedges`() {
        val result = decision(alternateLatencies = listOf(5_000L, 5_000L, 5_000L))
        val alternateTrace = result.trace.candidateEvaluations.single {
            it.releaseId == ChapterReleaseId("alternate")
        }

        assertTrue(checkNotNull(alternateTrace.remoteAccessScore).value < 8_000)
        assertHedgeOmitted(result)
    }

    @Test
    fun `alternate below reliability threshold never hedges`() {
        assertHedgeOmitted(decision(alternateReliability = 8_999))
    }

    @Test
    fun `local preferred alternate cannot borrow semantic score for remote hedge`() {
        val result = decision(
            alternateLocalFingerprint = "alternate-local",
            alternateLatencies = listOf(5_000L, 5_000L, 5_000L),
            continuity = ReadingContinuity(committedSourceId = PluginId("alternate-source")),
        )
        val alternateTrace = result.trace.candidateEvaluations.single {
            it.releaseId == ChapterReleaseId("alternate")
        }

        assertTrue(checkNotNull(alternateTrace.semanticWeightedScore).value >= 8_000)
        assertTrue(checkNotNull(alternateTrace.remoteAccessScore).value < 8_000)
        assertHedgeOmitted(result)
    }

    private fun assertHedgeOmitted(decision: ReaderRouteDecision) {
        assertIs<HedgeDirective.Omitted>(decision.hedgeDirective)
        assertNull(decision.competitiveSet.hedge)
    }

    private fun decision(
        intent: RoutingIntent = RoutingIntent.FOREGROUND,
        network: ReaderNetworkClass = ReaderNetworkClass.UNMETERED,
        primaryLocalFingerprint: String? = null,
        primaryLatencies: List<Long> = listOf(1_300L, 1_300L, 1_300L),
        alternateSource: String = "alternate-source",
        alternateRemoteAccess: CandidateRemoteAccess = CandidateRemoteAccess.PERMITTED,
        alternateLocalFingerprint: String? = null,
        alternateLatencies: List<Long> = listOf(200L, 200L, 200L),
        alternateReliability: Int = 10_000,
        continuity: ReadingContinuity = ReadingContinuity(),
    ): ReaderRouteDecision {
        val primary = candidate(
            release = "primary",
            source = "primary-source",
            localFingerprint = primaryLocalFingerprint,
        )
        val alternate = candidate(
            release = "alternate",
            source = alternateSource,
            remoteAccess = alternateRemoteAccess,
            localFingerprint = alternateLocalFingerprint,
        )
        val health = buildList {
            add(health("primary-source", primaryLatencies, reliability = 10_000))
            if (alternateSource != "primary-source") {
                add(health(alternateSource, alternateLatencies, alternateReliability))
            }
        }
        return engine.plan(
            snapshot = ReaderRoutingSnapshot.create(
                targetChapterId = CanonicalChapterId("chapter"),
                chapterGraphRevision = ReaderChapterGraphRevision(1),
                planRevision = ReaderPlanRevision(0),
                routingIntent = intent,
                candidates = listOf(primary, alternate),
                sourceHealth = health,
                continuity = continuity,
                networkClass = network,
                explicitReleaseId = ChapterReleaseId("primary"),
                nowEpochMillis = 1_000L,
            ),
            policy = ReaderRoutingPolicy.v1(),
        )
    }

    private fun candidate(
        release: String,
        source: String,
        remoteAccess: CandidateRemoteAccess = CandidateRemoteAccess.PERMITTED,
        localFingerprint: String? = null,
    ) = RoutingCandidate(
        releaseId = ChapterReleaseId(release),
        sourceId = PluginId(source),
        languageTag = "vi",
        sourceGroupKey = null,
        publishedAtEpochMillis = 1L,
        completeness = BasisPoints(10_000),
        remoteAccess = remoteAccess,
        localAccess = localFingerprint
            ?.let(CandidateLocalAccess::AvailableExact)
            ?: CandidateLocalAccess.Miss,
    )

    private fun health(
        source: String,
        latencies: List<Long>,
        reliability: Int,
    ) = SourceHealthSnapshot(
        key = SourceOperationKey(PluginId(source)),
        state = SourceHealthState(
            successEwmaBasisPoints = BasisPoints(reliability),
            recentLatencySamplesMillis = latencies,
        ),
        origin = SourceHealthOrigin.PROCESS_OBSERVED,
    )
}
