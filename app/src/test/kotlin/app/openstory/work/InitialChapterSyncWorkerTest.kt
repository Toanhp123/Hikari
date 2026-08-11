package app.openstory.work

import app.openstory.chapters.sync.ChapterSyncFailure
import app.openstory.chapters.sync.ChapterSyncReport
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class InitialChapterSyncWorkerTest {
    @Test
    fun workerDelegatesOnlyStableStoryIdAndMode() = runTest {
        var received: StoryId? = null

        val decision = runInitialChapterSyncWork("story:worker") { storyId ->
            received = storyId
            ChapterSyncReport.Success(emptyList())
        }

        assertEquals(StoryId("story:worker"), received)
        assertEquals(InitialChapterSyncWorkDecision.SUCCESS, decision)
    }

    @Test
    fun retryableSourceFailureRequestsRetry() = runTest {
        val decision = runInitialChapterSyncWork("story:worker") {
            ChapterSyncReport.Failure(
                listOf(ChapterSyncFailure(PluginId("org.example.content"), "plugin.timeout", true)),
            )
        }

        assertEquals(InitialChapterSyncWorkDecision.RETRY, decision)
    }

    @Test
    fun invalidStoryIdFailsWithoutDelegation() = runTest {
        var calls = 0

        val decision = runInitialChapterSyncWork(" ") {
            calls += 1
            ChapterSyncReport.Success(emptyList())
        }

        assertEquals(InitialChapterSyncWorkDecision.FAILURE, decision)
        assertEquals(0, calls)
    }
}
