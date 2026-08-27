package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.engine.internal.CandidateEvaluator
import app.openstory.reader.engine.internal.CandidateRanker
import app.openstory.reader.engine.internal.EligibilityEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals

class CandidateRankerTest {
    @Test
    fun weightedScoreUsesLongIntermediatesAndOneFinalDivision() {
        val policy = ReaderRoutingPolicy.v1()
        val snapshot = snapshot(listOf(candidate("a", "source")))
        val eligible = EligibilityEvaluator().evaluate(snapshot, policy).eligible
        val evaluated = CandidateEvaluator().evaluate(eligible, snapshot, policy).single()

        val expected = (
            evaluated.semanticFeatures.language.value.toLong() * policy.weights.language.value +
                evaluated.semanticFeatures.continuity.value.toLong() * policy.weights.continuity.value +
                evaluated.preferredAccessFeatures.health.value.toLong() * policy.weights.health.value +
                evaluated.preferredAccessFeatures.reliability.value.toLong() * policy.weights.reliability.value +
                evaluated.semanticFeatures.completeness.value.toLong() * policy.weights.completeness.value +
                evaluated.preferredAccessFeatures.latency.value.toLong() * policy.weights.latency.value +
                evaluated.semanticFeatures.freshness.value.toLong() * policy.weights.freshness.value +
                evaluated.preferredAccessFeatures.cacheUtility.value.toLong() * policy.weights.cacheUtility.value
            ) / 10_000L

        assertEquals(expected.toInt(), evaluated.weightedScore.value)
    }

    @Test
    fun tiesUseSourceThenReleaseAscendingAndIgnoreInputOrder() {
        val candidates = listOf(
            candidate("z", "a-source"),
            candidate("a", "a-source"),
            candidate("m", "z-source"),
        )
        val policy = ReaderRoutingPolicy.v1()
        fun rank(input: List<RoutingCandidate>): List<String> {
            val snapshot = snapshot(input)
            val eligible = EligibilityEvaluator().evaluate(snapshot, policy).eligible
            val evaluated = CandidateEvaluator().evaluate(eligible, snapshot, policy)
            return CandidateRanker().rank(evaluated).map { it.candidate.releaseId.value }
        }

        assertEquals(listOf("a", "z", "m"), rank(candidates))
        assertEquals(rank(candidates), rank(candidates.reversed()))
    }

    private fun candidate(release: String, source: String) = RoutingCandidate(
        releaseId = ChapterReleaseId(release),
        sourceId = PluginId(source),
        languageTag = "vi",
        sourceGroupKey = null,
        publishedAtEpochMillis = null,
        completeness = BasisPoints(10_000),
        remoteAccess = CandidateRemoteAccess.PERMITTED,
        localAccess = CandidateLocalAccess.Miss,
    )

    private fun snapshot(candidates: List<RoutingCandidate>) = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter"),
        chapterGraphRevision = ReaderChapterGraphRevision(1),
        planRevision = ReaderPlanRevision(0),
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = candidates,
        sourceHealth = emptyList(),
        continuity = ReadingContinuity(),
        networkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId = null,
        nowEpochMillis = 1L,
    )
}
