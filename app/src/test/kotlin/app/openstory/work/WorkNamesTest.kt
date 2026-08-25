package app.openstory.work

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkNamesTest {
    @Test
    fun preservesFrozenAndWave10UniqueWorkNames() {
        val storyId = StoryId("story:one")

        assertEquals("library-mapping:story:one", WorkNames.libraryMapping(storyId))
        assertEquals("initial-chapter-sync:story:one", WorkNames.storyChapterSync(storyId))
        assertEquals(
            "chapter-download:release:one",
            WorkNames.chapterDownload(ChapterReleaseId("release:one")),
        )
        assertEquals("canonical-engine-drain", WorkNames.CANONICAL_ENGINE_DRAIN)
        assertEquals("canonical-engine-safety", WorkNames.CANONICAL_ENGINE_SAFETY)
        assertEquals("canonical-engine-retry-wake:42", WorkNames.canonicalRetryWake(42))
        assertEquals("canonical-post-merge-derived:story:one", WorkNames.postMergeDerived(storyId))
        assertEquals("library-chapter-periodic", WorkNames.LIBRARY_CHAPTER_PERIODIC)
        assertEquals("library-chapter-continuation", WorkNames.LIBRARY_CHAPTER_CONTINUATION)
        assertEquals("chapter-notification-drain", WorkNames.CHAPTER_NOTIFICATION_DRAIN)
    }
}
