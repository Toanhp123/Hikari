package app.openstory.chapters.notification

import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterChangeDetectorTest {
    private val detector = ChapterChangeDetector()

    @Test
    fun retryProducesTheSameOrderedEventKeys() {
        val after = snapshot(chapter(), release(CanonicalChapterId("chapter:1")))
        val first = detector.detect(emptySnapshot(), after, "commit-1", 100)
        val retry = detector.detect(emptySnapshot(), after, "commit-1", 200)
        assertEquals(first.map { it.eventKey }, retry.map { it.eventKey })
        assertEquals(
            listOf(ChapterChangeKind.CANONICAL_CHAPTER_CREATED, ChapterChangeKind.RELEASE_LINKED),
            first.map { it.kind },
        )
    }

    @Test
    fun noOpDeletionAndTombstoneDoNotCreateFacts() {
        val current = snapshot(chapter(), release(CanonicalChapterId("chapter:1")))
        assertEquals(emptyList(), detector.detect(current, current, "commit-2", 100))
        assertEquals(
            emptyList(),
            detector.detect(current, snapshot(chapter(tombstoned = true)), "commit-3", 100),
        )
    }

    @Test
    fun restorationAndRelinkAreDetected() {
        val before = snapshot(chapter(tombstoned = true), release(null))
        val after = snapshot(chapter(), release(CanonicalChapterId("chapter:1")))
        assertEquals(
            listOf(ChapterChangeKind.CANONICAL_CHAPTER_RESTORED, ChapterChangeKind.RELEASE_LINKED),
            detector.detect(before, after, "commit-4", 100).map { it.kind },
        )
    }

    private fun emptySnapshot() = snapshot()

    private fun snapshot(
        chapter: CanonicalChapter? = null,
        release: ChapterRelease? = null,
    ) = ChapterGraphSnapshot(listOfNotNull(chapter), listOfNotNull(release), emptyList())

    private fun chapter(tombstoned: Boolean = false) = CanonicalChapter(
        CanonicalChapterId("chapter:1"),
        StoryId("story:1"),
        ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        "Chapter 1",
        tombstoned,
    )

    private fun release(chapterId: CanonicalChapterId?) = ChapterRelease(
        ChapterReleaseId("release:1"),
        StoryId("story:1"),
        PluginId("plugin:1"),
        "source-story",
        "source-release",
        "Chapter 1",
        ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        "en",
        1,
        chapterId,
    )
}
