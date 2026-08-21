package app.openstory.chapters.merge

import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterOverrideKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterSyncPhase
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.common.merge.DomainMergeDecision
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChapterStoryMergePolicyTest {
    private val policy = ChapterStoryMergePolicy()
    private val survivor = StoryId("story:a")
    private val retired = StoryId("story:b")

    @Test
    fun retiredChapterAndReleaseIdsMoveUnchangedWithoutDeduplication() {
        val survivorChapter = chapter(survivor, "chapter:a-10", "10")
        val retiredChapter = chapter(retired, "chapter:b-10", "10")
        val retiredRelease = release(retired, "release:b-10", retiredChapter.id)

        val plan = ready(
            input(
                survivorGraph = graph(chapters = listOf(survivorChapter)),
                retiredGraph = graph(chapters = listOf(retiredChapter), releases = listOf(retiredRelease)),
            ),
        )

        assertEquals(setOf(retiredChapter.id), plan.movedCanonicalChapterIds)
        assertEquals(setOf(retiredRelease.id), plan.movedReleaseIds)
        assertTrue(survivorChapter.id !in plan.movedCanonicalChapterIds)
        assertTrue(plan.requiresDerivedReaggregation)
    }

    @Test
    fun manualOverridesArePreserved() {
        val retiredChapter = chapter(retired, "chapter:b", "10")
        val retiredRelease = release(retired, "release:b", retiredChapter.id)
        val override = ChapterAggregationOverride(
            retiredRelease.id,
            retiredChapter.id,
            ChapterOverrideKind.FORCE_LINK,
        )

        val plan = ready(
            input(
                retiredGraph = graph(
                    chapters = listOf(retiredChapter),
                    releases = listOf(retiredRelease),
                    overrides = listOf(override),
                ),
            ),
        )

        assertEquals(listOf(override), plan.preservedOverrides)
    }

    @Test
    fun impossibleOverrideCollisionRequiresReview() {
        val releaseId = ChapterReleaseId("release:shared")
        val first = ChapterAggregationOverride(releaseId, CanonicalChapterId("chapter:a"), ChapterOverrideKind.FORCE_LINK)
        val second = ChapterAggregationOverride(releaseId, null, ChapterOverrideKind.FORCE_SEPARATE)

        val result = policy.plan(
            input(
                survivorGraph = graph(overrides = listOf(first)),
                retiredGraph = graph(overrides = listOf(second)),
            ),
        )

        assertEquals(
            setOf(CHAPTER_MANUAL_OVERRIDE_CONFLICT),
            assertIs<DomainMergeDecision.RequiresReview>(result).reasons,
        )
    }

    @Test
    fun nonCollidingSyncStateMovesToSurvivor() {
        val state = sync(retired, "p", "source-b", 20)

        val plan = ready(input(syncStates = listOf(state)))

        assertEquals(listOf(state.copy(storyId = survivor)), plan.syncStatesToMove)
        assertTrue(plan.syncKeysToInvalidate.isEmpty())
    }

    @Test
    fun postMergeSyncKeyCollisionInvalidatesInsteadOfMergingCursor() {
        val left = sync(survivor, "p", "same", 10).copy(cursor = "left")
        val right = sync(retired, "p", "same", 20).copy(cursor = "right")

        val plan = ready(input(syncStates = listOf(left, right)))

        assertTrue(plan.syncStatesToMove.isEmpty())
        assertEquals(setOf(ChapterSyncKey(PluginId("p"), "same")), plan.syncKeysToInvalidate)
        assertTrue(plan.requiresDerivedReaggregation)
    }

    @Test
    fun argumentOrderInsideSnapshotsDoesNotChangePlan() {
        val c1 = chapter(retired, "chapter:b1", "10")
        val c2 = chapter(retired, "chapter:b2", "11")
        val r1 = release(retired, "release:b1", c1.id)
        val r2 = release(retired, "release:b2", c2.id)
        val first = input(retiredGraph = graph(listOf(c1, c2), listOf(r1, r2)))
        val second = input(retiredGraph = graph(listOf(c2, c1), listOf(r2, r1)))

        assertEquals(policy.plan(first), policy.plan(second))
    }

    private fun ready(input: ChapterStoryMergeInput): ChapterStoryMergePlan =
        assertIs<DomainMergeDecision.Ready<ChapterStoryMergePlan>>(policy.plan(input)).value

    private fun input(
        survivorGraph: ChapterGraphSnapshot = graph(),
        retiredGraph: ChapterGraphSnapshot = graph(),
        syncStates: List<ChapterSyncState> = emptyList(),
    ) = ChapterStoryMergeInput(survivor, retired, survivorGraph, retiredGraph, syncStates)

    private fun graph(
        chapters: List<CanonicalChapter> = emptyList(),
        releases: List<ChapterRelease> = emptyList(),
        overrides: List<ChapterAggregationOverride> = emptyList(),
    ) = ChapterGraphSnapshot(chapters, releases, overrides)

    private fun chapter(storyId: StoryId, id: String, number: String) = CanonicalChapter(
        id = CanonicalChapterId(id),
        storyId = storyId,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, BigDecimal(number), null, null),
        displayLabel = "Chapter $number",
        tombstoned = false,
    )

    private fun release(storyId: StoryId, id: String, chapterId: CanonicalChapterId) = ChapterRelease(
        id = ChapterReleaseId(id),
        storyId = storyId,
        pluginId = PluginId("p"),
        sourceStoryId = "source",
        sourceReleaseId = id,
        displayLabel = id,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, BigDecimal.TEN, null, null),
        languageTag = "en",
        publishedAtEpochMillis = null,
        canonicalChapterId = chapterId,
    )

    private fun sync(storyId: StoryId, plugin: String, source: String, updatedAt: Long) = ChapterSyncState(
        storyId = storyId,
        pluginId = PluginId(plugin),
        sourceStoryId = source,
        phase = ChapterSyncPhase.INCREMENTAL,
        cursor = null,
        checkpoint = null,
        fingerprint = null,
        updatedAtEpochMillis = updatedAt,
    )
}
