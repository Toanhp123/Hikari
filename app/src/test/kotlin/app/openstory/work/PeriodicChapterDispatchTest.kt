package app.openstory.work

import app.openstory.chapters.sync.ChapterSyncBatch
import app.openstory.chapters.sync.ChapterSyncBatchCursor
import app.openstory.chapters.sync.ChapterSyncCandidate
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodicChapterDispatchTest {
    @Test
    fun oneStoryEnqueueFailureDoesNotStarveLaterCandidates() {
        val stories = listOf("story:a", "story:b", "story:c").map(::StoryId)
        val enqueued = mutableListOf<StoryId>()
        val decision = enqueuePeriodicChapterBatch(
            batch = ChapterSyncBatch(stories.map { ChapterSyncCandidate(it, null) }, continuation = null),
            enqueueStory = { storyId ->
                if (storyId == stories.first()) error("platform failure")
                enqueued += storyId
            },
            enqueueContinuation = {},
        )

        assertEquals(PeriodicDispatchEnqueueDecision.SUCCESS, decision)
        assertEquals(stories.drop(1), enqueued)
    }

    @Test
    fun continuationFailureRetriesOnlyTheDispatcherBoundary() {
        val cursor = ChapterSyncBatchCursor(1, StoryId("story:z"))
        val decision = enqueuePeriodicChapterBatch(
            batch = ChapterSyncBatch(emptyList(), cursor),
            enqueueStory = {},
            enqueueContinuation = { error("platform failure") },
        )

        assertEquals(PeriodicDispatchEnqueueDecision.RETRY, decision)
    }

    @Test
    fun storyWorkKeepsFrozenUniqueName() {
        assertEquals(
            "initial-chapter-sync:story:a",
            WorkNames.storyChapterSync(StoryId("story:a")),
        )
    }
}
