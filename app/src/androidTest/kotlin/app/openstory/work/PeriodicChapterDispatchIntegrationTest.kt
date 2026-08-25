package app.openstory.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.chapters.sync.ChapterSyncFailure
import app.openstory.chapters.sync.ChapterSyncBatchPlanner
import app.openstory.chapters.sync.ChapterSyncCandidate
import app.openstory.chapters.sync.ChapterSyncReport
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeriodicChapterDispatchIntegrationTest {
    @Test
    fun moreThanFortyDueStoriesDispatchInStableBoundedPagesWithoutStarvation() {
        val candidates = (0 until 45).map { index ->
            ChapterSyncCandidate(
                storyId = StoryId("story:${index.toString().padStart(2, '0')}"),
                lastSuccessfulSyncAtEpochMillis = if (index < 5) null else index.toLong(),
            )
        }
        val planner = ChapterSyncBatchPlanner()
        val cursorCodec = ChapterSyncCursorCodec()
        val pageSizes = mutableListOf<Int>()
        val enqueued = mutableListOf<StoryId>()
        var cursor: app.openstory.chapters.sync.ChapterSyncBatchCursor? = null
        var failedStory: StoryId? = null

        do {
            val batch = planner.plan(candidates, cursor)
            pageSizes += batch.selected.size
            val decision = enqueuePeriodicChapterBatch(
                batch = batch,
                enqueueStory = { storyId ->
                    if (failedStory == null) {
                        failedStory = storyId
                        error("isolated source enqueue failure")
                    }
                    enqueued += storyId
                },
                enqueueContinuation = {},
            )
            assertEquals(PeriodicDispatchEnqueueDecision.SUCCESS, decision)
            cursor = batch.continuation?.let { continuation ->
                cursorCodec.decode(cursorCodec.encode(continuation))
            }
        } while (cursor != null)

        assertEquals(listOf(20, 20, 5), pageSizes)
        assertEquals(44, enqueued.size)
        assertEquals(44, enqueued.distinct().size)
        assertTrue(failedStory !in enqueued)
        assertEquals(candidates.map { it.storyId }.toSet() - requireNotNull(failedStory), enqueued.toSet())
    }

    @Test
    fun cancellationEscapesStoryWorkInsteadOfBecomingRetry() = runBlocking {
        try {
            runInitialChapterSyncWork("story:cancelled") {
                throw CancellationException("cancelled")
            }
            fail("CancellationException must escape the worker decision boundary")
        } catch (_: CancellationException) {
            // Expected: WorkManager owns cancellation rather than receiving a retry decision.
        }
    }

    @Test
    fun retryableSourceFailureDoesNotBlockRemainingStoryWork() = runBlocking {
        val stories = listOf(StoryId("story:failed"), StoryId("story:next"), StoryId("story:last"))
        val decisions = stories.map { storyId ->
            runInitialChapterSyncWork(storyId.value) {
                if (storyId == stories.first()) {
                    ChapterSyncReport.Failure(
                        listOf(
                            ChapterSyncFailure(
                                pluginId = PluginId("org.example.content"),
                                code = "plugin.timeout",
                                retryable = true,
                            ),
                        ),
                    )
                } else {
                    ChapterSyncReport.Success(emptyList())
                }
            }
        }

        assertEquals(
            listOf(
                InitialChapterSyncWorkDecision.RETRY,
                InitialChapterSyncWorkDecision.SUCCESS,
                InitialChapterSyncWorkDecision.SUCCESS,
            ),
            decisions,
        )
    }
}
