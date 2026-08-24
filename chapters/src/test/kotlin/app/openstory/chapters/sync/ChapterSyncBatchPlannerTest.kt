package app.openstory.chapters.sync

import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChapterSyncBatchPlannerTest {
    private val planner = ChapterSyncBatchPlanner()

    @Test
    fun ordersNeverSyncedThenOldestWithStoryIdTieBreak() {
        val batch = planner.plan(
            listOf(
                candidate("story:c", 10),
                candidate("story:b", null),
                candidate("story:a", null),
                candidate("story:d", 5),
                candidate("story:e", 10),
            ),
        )

        assertEquals(
            listOf("story:a", "story:b", "story:d", "story:c", "story:e"),
            batch.selected.map { it.storyId.value },
        )
        assertNull(batch.continuation)
    }

    @Test
    fun selectsAtMostTwentyAndContinuesStrictlyAfterCursor() {
        val candidates = (0 until 45).map { index -> candidate("story:${index.toString().padStart(2, '0')}", 1) }
        val first = planner.plan(candidates)
        val second = planner.plan(candidates, first.continuation)
        val third = planner.plan(candidates, second.continuation)

        assertEquals(20, first.selected.size)
        assertEquals(20, second.selected.size)
        assertEquals(5, third.selected.size)
        assertEquals(45, (first.selected + second.selected + third.selected).map { it.storyId }.distinct().size)
        assertNull(third.continuation)
    }

    @Test
    fun emptyInputProducesEmptyTerminalBatch() {
        val batch = planner.plan(emptyList())
        assertEquals(emptyList(), batch.selected)
        assertNull(batch.continuation)
    }

    private fun candidate(id: String, timestamp: Long?) = ChapterSyncCandidate(StoryId(id), timestamp)
}
