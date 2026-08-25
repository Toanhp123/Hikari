package app.openstory.reader.routing

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.engine.BasisPoints
import app.openstory.reader.engine.CandidateLocalAccess
import app.openstory.reader.engine.CandidateRemoteAccess
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.engine.ReaderNetworkClass
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.engine.ReaderRouteEngine
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot
import app.openstory.reader.engine.ReadingContinuity
import app.openstory.reader.engine.RoutingCandidate
import app.openstory.reader.engine.RoutingIntent
import app.openstory.reader.engine.SourceGroupKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * L6 migration differential retained after production legacy-selector deletion.
 *
 * HES-v1 intentionally diverges from the old automatic comparator. The only final overlap envelope
 * is an explicit, access-eligible release, so the legacy oracle lives in test scope rather than
 * keeping a second ranking algorithm reachable from production Reader code.
 */
class ReaderRouteEngineDifferentialTest {
    private val engine = ReaderRouteEngine.v1()

    @Test
    fun seededExplicitSelectionOverlapMatchesLegacyOracleForAtLeastTwoHundredCandidateSets() {
        val random = Random(0x48455331)

        repeat(250) { fixtureIndex ->
            val candidates = candidates(random, fixtureIndex)
            val explicit = candidates[random.nextInt(candidates.size)]
            val policy = LegacyFixturePolicy(
                explicitReleaseId = explicit.releaseId,
                previousReleaseId = candidates.randomOrNull(random)?.releaseId,
                previousPluginId = candidates.randomOrNull(random)?.sourceId,
                previousSourceGroup = candidates.mapNotNull { it.sourceGroup }.randomOrNull(random),
                languageOrder = listOf("vi", "en", "ja", "fr").shuffled(random).take(random.nextInt(0, 5)),
            )
            val legacyWinner = legacyExplicitWinner(candidates, policy)
            val decision = engine.plan(
                snapshot(
                    fixtureIndex = fixtureIndex,
                    candidates = candidates,
                    policy = policy,
                ),
                ReaderRoutingPolicy.v1(languageOrder = policy.languageOrder),
            )

            assertEquals(
                legacyWinner.releaseId,
                decision.competitiveSet.primary?.releaseId,
                "explicit winner mismatch in seeded fixture $fixtureIndex",
            )
        }
    }

    private fun legacyExplicitWinner(
        candidates: List<LegacyFixtureCandidate>,
        policy: LegacyFixturePolicy,
    ): LegacyFixtureCandidate = candidates.first { it.releaseId == policy.explicitReleaseId }

    private fun snapshot(
        fixtureIndex: Int,
        candidates: List<LegacyFixtureCandidate>,
        policy: LegacyFixturePolicy,
    ): ReaderRoutingSnapshot = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter-$fixtureIndex"),
        chapterGraphRevision = ReaderChapterGraphRevision(fixtureIndex.toLong()),
        planRevision = ReaderPlanRevision(fixtureIndex.toLong()),
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = candidates.map { candidate ->
            RoutingCandidate(
                releaseId = candidate.releaseId,
                sourceId = candidate.sourceId,
                languageTag = candidate.languageTag,
                sourceGroupKey = candidate.sourceGroup?.let(::SourceGroupKey),
                publishedAtEpochMillis = candidate.publishedAtEpochMillis,
                completeness = BasisPoints(candidate.completeness * 100),
                remoteAccess = CandidateRemoteAccess.PERMITTED,
                localAccess = CandidateLocalAccess.Miss,
            )
        },
        sourceHealth = emptyList(),
        continuity = ReadingContinuity(
            committedSourceId = policy.previousPluginId,
            committedSourceGroupKey = policy.previousSourceGroup?.let(::SourceGroupKey),
            targetResumeReleaseId = policy.previousReleaseId,
        ),
        networkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId = policy.explicitReleaseId,
        nowEpochMillis = 0L,
    )

    private fun candidates(random: Random, fixtureIndex: Int): List<LegacyFixtureCandidate> {
        val count = random.nextInt(from = 1, until = 13)
        val languages = listOf("vi", "en", "ja", "fr")
        val groups = listOf<String?>(null, "group-a", "group-b")
        return List(count) { candidateIndex ->
            LegacyFixtureCandidate(
                releaseId = ChapterReleaseId("release-$fixtureIndex-$candidateIndex"),
                sourceId = PluginId("source-${random.nextInt(0, 5)}"),
                languageTag = languages[random.nextInt(languages.size)],
                sourceGroup = groups[random.nextInt(groups.size)],
                completeness = random.nextInt(0, 101),
                publishedAtEpochMillis = if (random.nextInt(5) == 0) null else random.nextLong(0L, 10_000L),
            )
        }.shuffled(random)
    }

    private data class LegacyFixtureCandidate(
        val releaseId: ChapterReleaseId,
        val sourceId: PluginId,
        val languageTag: String,
        val sourceGroup: String?,
        val completeness: Int,
        val publishedAtEpochMillis: Long?,
    )

    private data class LegacyFixturePolicy(
        val explicitReleaseId: ChapterReleaseId,
        val previousReleaseId: ChapterReleaseId?,
        val previousPluginId: PluginId?,
        val previousSourceGroup: String?,
        val languageOrder: List<String>,
    )
}
