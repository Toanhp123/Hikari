package app.openstory.chapters.notification

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChapterNotificationClassifierTest {
    private val classifier = ChapterNotificationClassifier()
    private val policy = ChapterNotificationPolicy(true, true, listOf("vi", "en"))

    @Test
    fun newChapterChoosesTheFirstPreferredAvailableRelease() {
        val decision = classifier.classify(fact(ChapterChangeKind.CANONICAL_CHAPTER_CREATED), context(), policy)
        val publish = assertIs<ChapterNotificationDecision.Publish>(decision)
        assertEquals(ChapterReleaseId("release:vi"), publish.candidate.releaseId)
    }

    @Test
    fun releaseCoveredByNewChapterIsConsumed() {
        val decision = classifier.classify(
            fact(ChapterChangeKind.RELEASE_LINKED, ChapterReleaseId("release:vi")),
            context(),
            policy,
            coveredByNewChapterEvent = true,
        )
        assertEquals(
            ChapterNotificationDecision.Consume("notification.covered_by_new_chapter"),
            decision,
        )
    }

    @Test
    fun readTombstonedAndMissingTargetsAreConsumed() {
        assertIs<ChapterNotificationDecision.Consume>(
            classifier.classify(fact(ChapterChangeKind.CANONICAL_CHAPTER_CREATED), null, policy),
        )
        assertIs<ChapterNotificationDecision.Consume>(
            classifier.classify(
                fact(ChapterChangeKind.CANONICAL_CHAPTER_CREATED),
                context().copy(chapterRead = true),
                policy,
            ),
        )
        assertIs<ChapterNotificationDecision.Consume>(
            classifier.classify(
                fact(ChapterChangeKind.CANONICAL_CHAPTER_CREATED),
                context().copy(chapterTombstoned = true),
                policy,
            ),
        )
    }

    private fun fact(kind: ChapterChangeKind, releaseId: ChapterReleaseId? = null) = ChapterChangeFact(
        "event", StoryId("story:1"), CanonicalChapterId("chapter:1"), releaseId, kind, "commit", 1,
    )

    private fun context() = ChapterNotificationContext(
        StoryId("story:1"),
        CanonicalChapterId("chapter:1"),
        chapterTombstoned = false,
        chapterRead = false,
        releases = listOf(
            ChapterNotificationRelease(ChapterReleaseId("release:en"), "en"),
            ChapterNotificationRelease(ChapterReleaseId("release:vi"), "vi"),
        ),
    )
}
