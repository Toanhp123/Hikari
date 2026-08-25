package app.openstory.work

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkInputTest {
    @Test
    fun parsesStableIdsWithoutLeakingConstructorExceptions() {
        assertEquals(StoryId("story:one"), WorkInput.storyId("story:one").getOrThrow())
        assertEquals(
            ChapterReleaseId("release:one"),
            WorkInput.chapterReleaseId("release:one").getOrThrow(),
        )
        assertTrue(WorkInput.storyId(null).isFailure)
        assertTrue(WorkInput.storyId(" ").isFailure)
        assertTrue(WorkInput.chapterReleaseId(null).isFailure)
        assertTrue(WorkInput.chapterReleaseId("bad id").isFailure)
    }

    @Test
    fun periodicCursorUsesVersionedInputKey() {
        assertEquals("chapter_sync_cursor_v1", WorkInput.CHAPTER_SYNC_CURSOR_V1)
    }
}
