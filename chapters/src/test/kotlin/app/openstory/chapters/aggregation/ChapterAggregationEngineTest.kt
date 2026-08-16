package app.openstory.chapters.aggregation

import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterOverrideKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ChapterAggregationEngineTest {
    private val engine = ChapterAggregationEngine()

    @Test
    fun explicitNumberConflictNeverLinksToExistingChapter() {
        val existing = chapter("existing-10", numbered("10"))
        val release = release("release-11", numbered("11"))

        val plan = engine.plan(STORY_ID, listOf(existing), listOf(release), emptyList())

        assertTrue(plan.links.none { it.canonicalChapterId == existing.id })
        assertEquals(1, plan.creates.size)
    }

    @Test
    fun equivalentReleasesCreateOneCanonicalChapter() {
        val first = release("source-a-10", numbered("10"), plugin = "plugin-a")
        val second = release("source-b-10", numbered("10"), plugin = "plugin-b")

        val plan = engine.plan(STORY_ID, emptyList(), listOf(first, second), emptyList())

        assertEquals(1, plan.creates.size)
        assertEquals(2, plan.links.size)
        assertEquals(1, plan.links.map { it.canonicalChapterId }.distinct().size)
    }

    @Test
    fun planIsIndependentOfInputOrder() {
        val releases = listOf(
            release("release-2", numbered("2")),
            release("release-1", numbered("1")),
            release("release-extra", named(ChapterKind.EXTRA, "bonus")),
        )

        assertEquals(
            engine.plan(STORY_ID, emptyList(), releases, emptyList()),
            engine.plan(STORY_ID, emptyList(), releases.reversed(), emptyList()),
        )
    }

    @Test
    fun mediumConfidenceCandidatesRemainSeparateForReview() {
        val first = release("unknown-a", named(ChapterKind.UNKNOWN, "festival"), plugin = "plugin-a")
        val second = release("unknown-b", named(ChapterKind.UNKNOWN, "festival"), plugin = "plugin-b")

        val plan = engine.plan(STORY_ID, emptyList(), listOf(first, second), emptyList())

        assertEquals(2, plan.creates.size)
        assertNotEquals(plan.links[0].canonicalChapterId, plan.links[1].canonicalChapterId)
        assertEquals(1, plan.reviewCandidates.size)
    }

    @Test
    fun equalScoreTieBreakUsesLexicographicallySmallestChapterId() {
        val parsed = numbered("10")
        val later = chapter("chapter-z", parsed)
        val earlier = chapter("chapter-a", parsed)
        val incoming = release("release-10", parsed)

        val plan = engine.plan(STORY_ID, listOf(later, earlier), listOf(incoming), emptyList())

        assertEquals(
            listOf(ChapterReleaseLink(incoming.id, earlier.id)),
            plan.links,
        )
    }

    @Test
    fun protectedLinkOverrideOutranksNumberConflict() {
        val existing = chapter("existing-10", numbered("10"))
        val release = release("release-11", numbered("11"))
        val override = ChapterAggregationOverride(
            releaseId = release.id,
            canonicalChapterId = existing.id,
            kind = ChapterOverrideKind.FORCE_LINK,
        )

        val plan = engine.plan(STORY_ID, listOf(existing), listOf(release), listOf(override))

        assertEquals(listOf(ChapterReleaseLink(release.id, existing.id)), plan.links)
        assertTrue(plan.creates.isEmpty())
    }

    @Test
    fun chapterWithOnlyMissingReleasesBecomesTombstone() {
        val missingReleaseId = ChapterReleaseId("missing-release")
        val existing = chapter("existing-10", numbered("10")).copy(releaseIds = setOf(missingReleaseId))

        val plan = engine.plan(STORY_ID, listOf(existing), emptyList(), emptyList())

        assertEquals(setOf(existing.id), plan.tombstones)
    }

    private fun chapter(id: String, parsed: ParsedChapterLabel) = CanonicalChapter(
        id = CanonicalChapterId(id),
        storyId = STORY_ID,
        parsedLabel = parsed,
        displayLabel = id,
        tombstoned = false,
    )

    private fun release(
        id: String,
        parsed: ParsedChapterLabel,
        plugin: String = "plugin",
    ) = ChapterRelease(
        id = ChapterReleaseId(id),
        storyId = STORY_ID,
        pluginId = PluginId(plugin),
        sourceStoryId = "source-story",
        sourceReleaseId = id,
        displayLabel = id,
        parsedLabel = parsed,
        languageTag = "en",
        publishedAtEpochMillis = null,
        canonicalChapterId = null,
    )

    private fun numbered(value: String) = ParsedChapterLabel(
        ChapterKind.NUMBERED,
        null,
        BigDecimal(value),
        null,
        null,
    )

    private fun named(kind: ChapterKind, title: String) = ParsedChapterLabel(
        kind,
        null,
        null,
        null,
        title,
    )

    private companion object {
        val STORY_ID = StoryId("story")
    }
}
