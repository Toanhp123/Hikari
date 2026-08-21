package app.openstory.reader.progress

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.common.merge.DomainMergeDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReadingProgressMergePolicyTest {
    private val policy = ReadingProgressMergePolicy()
    private val survivor = StoryId("story:survivor")

    @Test
    fun differentCanonicalChaptersAllSurviveWithSurvivorOwnership() {
        val a = progress("story:a", "chapter:a", "release:a", "fp-a", 0.25f, null, 10)
        val b = progress("story:b", "chapter:b", "release:b", "fp-b", 0.75f, null, 20)

        val result = ready(policy.plan(survivor, listOf(a), listOf(b)))

        assertEquals(setOf(a.canonicalChapterId, b.canonicalChapterId), result.progressRows.map { it.canonicalChapterId }.toSet())
        assertEquals(setOf(survivor), result.progressRows.map { it.storyId }.toSet())
    }

    @Test
    fun sameChapterSameFingerprintPrefersCompleted() {
        val incomplete = progress("story:a", "chapter:x", "release:x", "same", 0.95f, null, 50)
        val completed = progress("story:b", "chapter:x", "release:x", "same", 0.50f, 40, 40)

        val result = ready(policy.plan(survivor, listOf(incomplete), listOf(completed)))

        assertEquals(completed.copy(storyId = survivor), result.progressRows.single())
    }

    @Test
    fun sameChapterSameFingerprintPrefersLargerFractionWhenIncomplete() {
        val lower = progress("story:a", "chapter:x", "release:x", "same", 0.25f, null, 100)
        val higher = progress("story:b", "chapter:x", "release:x", "same", 0.75f, null, 50)

        assertEquals(
            higher.copy(storyId = survivor),
            ready(policy.plan(survivor, listOf(lower), listOf(higher))).progressRows.single(),
        )
    }

    @Test
    fun differentFingerprintsOnDifferentReleasesRequireReview() {
        val left = progress("story:a", "chapter:x", "release:a", "fp-a", 0.8f, null, 10)
        val right = progress("story:b", "chapter:x", "release:b", "fp-b", 0.9f, null, 20)

        val result = policy.plan(survivor, listOf(left), listOf(right))

        assertEquals(
            setOf(READING_PROGRESS_UNSAFE_CONFLICT),
            assertIs<DomainMergeDecision.RequiresReview>(result).reasons,
        )
    }

    @Test
    fun differentFingerprintsOnSameReleaseCompareOnlyWhenCurrentBlockIsStable() {
        val left = progress(
            "story:a", "chapter:x", "release:x", "fp-a", 0.8f, null, 10,
            block = "block-stable", offset = 10,
        )
        val right = progress(
            "story:b", "chapter:x", "release:x", "fp-b", 0.2f, null, 20,
            block = "block-stable", offset = 40,
        )

        val result = ready(policy.plan(survivor, listOf(left), listOf(right)))

        assertEquals(right.copy(storyId = survivor), result.progressRows.single())
    }

    @Test
    fun differentFingerprintsOnSameReleaseButDifferentBlocksRequireReview() {
        val left = progress(
            "story:a", "chapter:x", "release:x", "fp-a", 0.8f, null, 10,
            block = "block-a", offset = 10,
        )
        val right = progress(
            "story:b", "chapter:x", "release:x", "fp-b", 0.9f, null, 20,
            block = "block-b", offset = 40,
        )

        assertIs<DomainMergeDecision.RequiresReview>(policy.plan(survivor, listOf(left), listOf(right)))
    }

    @Test
    fun resultKeepsChapterAndReleaseIdsUnchanged() {
        val row = progress("story:b", "chapter:x", "release:x", "fp", 0.5f, null, 10)

        val merged = ready(policy.plan(survivor, emptyList(), listOf(row))).progressRows.single()

        assertEquals(row.canonicalChapterId, merged.canonicalChapterId)
        assertEquals(row.releaseId, merged.releaseId)
    }

    @Test
    fun argumentOrderIsIrrelevant() {
        val left = progress("story:a", "chapter:x", "release:x", "same", 0.25f, null, 10)
        val right = progress("story:b", "chapter:x", "release:x", "same", 0.75f, null, 20)

        assertEquals(
            policy.plan(survivor, listOf(left), listOf(right)),
            policy.plan(survivor, listOf(right), listOf(left)),
        )
    }

    private fun ready(decision: DomainMergeDecision<ReadingProgressMergePlan>) =
        assertIs<DomainMergeDecision.Ready<ReadingProgressMergePlan>>(decision).value

    private fun progress(
        story: String,
        chapter: String,
        release: String,
        fingerprint: String,
        fraction: Float,
        completed: Long?,
        updated: Long,
        block: String = "block",
        offset: Int = 0,
    ) = ReadingProgress(
        storyId = StoryId(story),
        canonicalChapterId = CanonicalChapterId(chapter),
        releaseId = ChapterReleaseId(release),
        contentFingerprint = fingerprint,
        position = ReadingPosition(block, offset, fraction),
        completedAtEpochMillis = completed,
        updatedAtEpochMillis = updated,
    )
}
