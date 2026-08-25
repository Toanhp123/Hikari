package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.engine.internal.CandidateEvaluator
import app.openstory.reader.engine.internal.EligibilityEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals

class CandidateEvaluatorTest {
    private val eligibility = EligibilityEvaluator()
    private val evaluator = CandidateEvaluator()

    @Test
    fun languageNormalizationUsesExactOrderedAllowBoundaries() {
        val candidates = listOf("vi", "en", "ja", "fr", "de").mapIndexed { index, language ->
            candidate("r$index", "s$index", language = language)
        }
        val policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en", "ja", "fr"))
        val evaluated = evaluate(candidates, policy = policy).associateBy { it.candidate.languageTag }

        assertEquals(10_000, evaluated.getValue("vi").semanticFeatures.language.value)
        assertEquals(8_000, evaluated.getValue("en").semanticFeatures.language.value)
        assertEquals(6_000, evaluated.getValue("ja").semanticFeatures.language.value)
        assertEquals(4_000, evaluated.getValue("fr").semanticFeatures.language.value)
        assertEquals(2_000, evaluated.getValue("de").semanticFeatures.language.value)

        val emptyOrder = evaluate(listOf(candidate("x", "sx", language = "xx")))
        assertEquals(10_000, emptyOrder.single().semanticFeatures.language.value)
    }

    @Test
    fun localPreferredPathUsesNeutralRemoteIndependentFeatures() {
        val local = candidate(
            "local",
            "source",
            local = CandidateLocalAccess.AvailableExact("fp"),
        )
        val evaluated = evaluate(
            listOf(local),
            health = listOf(health("source", CircuitState.OPEN, reliability = 1_000, samples = listOf(9_000L, 9_000L, 9_000L))),
        ).single()

        assertEquals(AccessMode.LOCAL, evaluated.preferredAccessMode)
        assertEquals(10_000, evaluated.preferredAccessFeatures.health.value)
        assertEquals(10_000, evaluated.preferredAccessFeatures.reliability.value)
        assertEquals(10_000, evaluated.preferredAccessFeatures.latency.value)
        assertEquals(10_000, evaluated.preferredAccessFeatures.cacheUtility.value)
        assertEquals(null, evaluated.remoteAccessScore)
    }

    @Test
    fun unverifiedLocalGetsSixThousandCacheUtility() {
        val evaluated = evaluate(
            listOf(candidate("local", "source", local = CandidateLocalAccess.AvailableUnverified("fp"))),
        ).single()
        assertEquals(6_000, evaluated.preferredAccessFeatures.cacheUtility.value)
    }

    @Test
    fun remoteHealthReliabilityAndLatencyUseExactBoundaries() {
        val latencies = listOf(250L, 500L, 1_000L, 2_000L, 4_000L, 4_001L)
        val expected = listOf(10_000, 8_500, 6_500, 4_000, 2_000, 1_000)
        latencies.zip(expected).forEachIndexed { index, (latency, expectedScore) ->
            val source = "s$index"
            val evaluated = evaluate(
                listOf(candidate("r$index", source)),
                health = listOf(health(source, CircuitState.CLOSED, reliability = 8_765, samples = listOf(latency, latency, latency))),
            ).single()
            assertEquals(10_000, evaluated.preferredAccessFeatures.health.value)
            assertEquals(8_765, evaluated.preferredAccessFeatures.reliability.value)
            assertEquals(expectedScore, evaluated.preferredAccessFeatures.latency.value)
            assertEquals(0, evaluated.preferredAccessFeatures.cacheUtility.value)
        }

        val neutral = evaluate(
            listOf(candidate("neutral", "neutral-source")),
            health = listOf(health("neutral-source", CircuitState.CLOSED, samples = listOf(100L, 200L))),
        ).single()
        assertEquals(5_000, neutral.preferredAccessFeatures.latency.value)
    }

    @Test
    fun continuityUsesExactTargetGroupSourceLanguagePrecedence() {
        val targetResume = candidate("target-resume", "resume-source", language = "fr")
        val sameCommittedTarget = candidate("committed", "committed-source", language = "fr")
        val trustedGroup = candidate("group", "other-source", language = "fr", sourceGroup = "trusted")
        val sameSource = candidate("source", "committed-source", language = "fr")
        val sameLanguage = candidate("language", "different-source", language = "vi")
        val unrelated = candidate("unrelated", "different-source-2", language = "ja")
        val continuity = ReadingContinuity(
            committedChapterId = CanonicalChapterId("chapter"),
            committedReleaseId = ChapterReleaseId("committed"),
            committedSourceId = PluginId("committed-source"),
            committedSourceGroupKey = SourceGroupKey("trusted"),
            committedLanguageTag = "vi",
            targetResumeReleaseId = ChapterReleaseId("target-resume"),
        )

        val scores = evaluate(
            listOf(targetResume, sameCommittedTarget, trustedGroup, sameSource, sameLanguage, unrelated),
            continuity = continuity,
        ).associate { it.candidate.releaseId.value to it.semanticFeatures.continuity.value }

        assertEquals(10_000, scores.getValue("target-resume"))
        assertEquals(10_000, scores.getValue("committed"))
        assertEquals(8_000, scores.getValue("group"))
        assertEquals(6_500, scores.getValue("source"))
        assertEquals(2_000, scores.getValue("language"))
        assertEquals(0, scores.getValue("unrelated"))
    }

    @Test
    fun absentTrustedGroupFactDoesNotGrantGroupContinuityBonus() {
        val evaluated = evaluate(
            listOf(candidate("candidate", "other-source", sourceGroup = "trusted")),
            continuity = ReadingContinuity(
                committedSourceGroupKey = null,
                committedSourceId = PluginId("committed-source"),
            ),
        ).single()

        assertEquals(0, evaluated.semanticFeatures.continuity.value)
    }

    @Test
    fun heldHalfOpenRemoteGetsSixThousandHealth() {
        val candidate = candidate("half", "half-source")
        val snapshot = snapshot(
            candidates = listOf(candidate),
            health = listOf(health("half-source", CircuitState.HALF_OPEN, probe = true)),
        )
        val eligible = eligibility.evaluate(snapshot, ReaderRoutingPolicy.v1()).eligible
        val evaluated = evaluator.evaluate(eligible, snapshot, ReaderRoutingPolicy.v1()).single()
        assertEquals(6_000, evaluated.preferredAccessFeatures.health.value)
    }

    @Test
    fun freshnessUsesNewestKnownEligibleTimestampAndUnknownIsNeutral() {
        val hour = 60L * 60L * 1000L
        val day = 24L * hour
        val newest = 100L * day
        val candidates = listOf(
            candidate("new", "s0", publishedAt = newest),
            candidate("hour", "s1", publishedAt = newest - hour),
            candidate("day", "s2", publishedAt = newest - day),
            candidate("week", "s3", publishedAt = newest - 7L * day),
            candidate("month", "s4", publishedAt = newest - 30L * day),
            candidate("old", "s5", publishedAt = newest - 31L * day),
            candidate("unknown", "s6", publishedAt = null),
        )
        val scores = evaluate(candidates).associate { it.candidate.releaseId.value to it.semanticFeatures.freshness.value }
        assertEquals(10_000, scores.getValue("new"))
        assertEquals(10_000, scores.getValue("hour"))
        assertEquals(9_000, scores.getValue("day"))
        assertEquals(7_500, scores.getValue("week"))
        assertEquals(6_000, scores.getValue("month"))
        assertEquals(4_000, scores.getValue("old"))
        assertEquals(5_000, scores.getValue("unknown"))

        val allUnknown = evaluate(listOf(candidate("a", "a", publishedAt = null), candidate("b", "b", publishedAt = null)))
        assertEquals(listOf(5_000, 5_000), allUnknown.map { it.semanticFeatures.freshness.value })
    }

    private fun evaluate(
        candidates: List<RoutingCandidate>,
        policy: ReaderRoutingPolicy = ReaderRoutingPolicy.v1(),
        health: List<SourceHealthSnapshot> = emptyList(),
        continuity: ReadingContinuity = ReadingContinuity(),
    ) = snapshot(candidates, health, continuity).let { snapshot ->
        evaluator.evaluate(eligibility.evaluate(snapshot, policy).eligible, snapshot, policy)
    }

    private fun candidate(
        release: String,
        source: String,
        language: String = "vi",
        publishedAt: Long? = 1L,
        local: CandidateLocalAccess = CandidateLocalAccess.Miss,
        sourceGroup: String? = null,
    ) = RoutingCandidate(
        releaseId = ChapterReleaseId(release),
        sourceId = PluginId(source),
        languageTag = language,
        sourceGroupKey = sourceGroup?.let(::SourceGroupKey),
        publishedAtEpochMillis = publishedAt,
        completeness = BasisPoints(10_000),
        remoteAccess = CandidateRemoteAccess.PERMITTED,
        localAccess = local,
    )

    private fun snapshot(
        candidates: List<RoutingCandidate>,
        health: List<SourceHealthSnapshot> = emptyList(),
        continuity: ReadingContinuity = ReadingContinuity(),
    ) = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter"),
        chapterGraphRevision = ReaderChapterGraphRevision(1),
        planRevision = ReaderPlanRevision(0),
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = candidates,
        sourceHealth = health,
        continuity = continuity,
        networkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId = null,
        nowEpochMillis = 1L,
    )

    private fun health(
        source: String,
        circuit: CircuitState,
        reliability: Int = 10_000,
        samples: List<Long> = emptyList(),
        probe: Boolean = false,
    ) = SourceHealthSnapshot(
        key = SourceOperationKey(PluginId(source)),
        state = when (circuit) {
            CircuitState.CLOSED -> SourceHealthState(
                successEwmaBasisPoints = BasisPoints(reliability),
                recentLatencySamplesMillis = samples,
            )
            CircuitState.OPEN -> SourceHealthState(
                circuitState = CircuitState.OPEN,
                successEwmaBasisPoints = BasisPoints(reliability),
                recentLatencySamplesMillis = samples,
                openCount = 1,
                openedAtEpochMillis = 0L,
                nextProbeAtEpochMillis = 1L,
            )
            CircuitState.HALF_OPEN -> SourceHealthState(
                circuitState = CircuitState.HALF_OPEN,
                successEwmaBasisPoints = BasisPoints(reliability),
                recentLatencySamplesMillis = samples,
                openCount = 1,
                openedAtEpochMillis = 0L,
                nextProbeAtEpochMillis = 1L,
            )
        },
        origin = SourceHealthOrigin.PROCESS_OBSERVED,
        halfOpenProbePermitted = probe,
    )
}
