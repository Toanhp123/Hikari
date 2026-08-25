package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.engine.ReaderRouteEngine
import app.openstory.reader.selection.ReleaseCandidate
import app.openstory.reader.selection.ReleaseHealth
import app.openstory.reader.selection.ReleaseSelectionPolicy
import app.openstory.reader.selection.ReleaseSelectionResult
import app.openstory.reader.selection.ReleaseSelector
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderRouteEngineDifferentialTest {
    private val selector = ReleaseSelector()
    private val engine = ReaderRouteEngine.v1()

    @Test
    fun seededLegacyOverlapEnvelopeMatchesForAtLeastTwoHundredCandidateSets() {
        val random = Random(0x48455331)

        repeat(250) { fixtureIndex ->
            val candidates = candidates(random, fixtureIndex)
            val policy = policy(random, candidates)
            val legacy = assertIs<ReleaseSelectionResult.Selected>(selector.select(candidates, policy))
            val snapshot = LegacyReaderRoutingAdapter.compatibilitySnapshot(
                targetChapterId = CanonicalChapterId("chapter-$fixtureIndex"),
                candidates = candidates,
                selectionPolicy = policy,
                chapterGraphRevision = ReaderChapterGraphRevision(fixtureIndex.toLong()),
                planRevision = ReaderPlanRevision(fixtureIndex.toLong()),
            )
            val decision = engine.plan(snapshot, LegacyReaderRoutingAdapter.compatibilityPolicy(policy))

            assertEquals(
                legacy.candidate.release.id,
                decision.competitiveSet.primary?.releaseId,
                "winner mismatch in seeded fixture $fixtureIndex",
            )
            assertEquals(
                legacy.alternates.map { it.release.id }.distinct(),
                decision.trace.stableRanking.drop(1).distinct(),
                "alternate order mismatch in seeded fixture $fixtureIndex",
            )
        }
    }

    private fun candidates(random: Random, fixtureIndex: Int): List<ReleaseCandidate> {
        val count = random.nextInt(from = 1, until = 13)
        val languages = listOf("vi", "en", "ja", "fr")
        val groups = listOf<String?>(null, "group-a", "group-b")
        return List(count) { candidateIndex ->
            val sourceIndex = random.nextInt(0, 5)
            val id = "release-$fixtureIndex-$candidateIndex"
            ReleaseCandidate(
                release = ChapterRelease(
                    id = ChapterReleaseId(id),
                    storyId = StoryId("story"),
                    pluginId = PluginId("source-$sourceIndex"),
                    sourceStoryId = "source-story",
                    sourceReleaseId = "remote-$id",
                    displayLabel = id,
                    parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
                    languageTag = languages[random.nextInt(languages.size)],
                    publishedAtEpochMillis = if (random.nextInt(5) == 0) null else random.nextLong(0L, 10_000L),
                    canonicalChapterId = CanonicalChapterId("chapter-$fixtureIndex"),
                ),
                sourceGroup = groups[random.nextInt(groups.size)],
                health = ReleaseHealth.HEALTHY,
                completeness = random.nextInt(0, 101),
            )
        }.shuffled(random)
    }

    private fun policy(
        random: Random,
        candidates: List<ReleaseCandidate>,
    ): ReleaseSelectionPolicy {
        fun maybeCandidate(): ReleaseCandidate? =
            if (random.nextBoolean()) candidates[random.nextInt(candidates.size)] else null

        val groupCandidates = candidates.mapNotNull { it.sourceGroup }.distinct()
        val languageOrder = listOf("vi", "en", "ja", "fr").shuffled(random).take(random.nextInt(0, 5))
        return ReleaseSelectionPolicy(
            explicitReleaseId = maybeCandidate()?.release?.id,
            previousReleaseId = maybeCandidate()?.release?.id,
            previousPluginId = maybeCandidate()?.release?.pluginId,
            previousSourceGroup = if (groupCandidates.isNotEmpty() && random.nextBoolean()) {
                groupCandidates[random.nextInt(groupCandidates.size)]
            } else {
                null
            },
            languageOrder = languageOrder,
        )
    }
}
