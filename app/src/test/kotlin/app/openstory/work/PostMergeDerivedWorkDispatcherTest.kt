package app.openstory.work

import app.openstory.catalog.orchestration.PostMergeDerivedRequirements
import app.openstory.catalog.orchestration.PostMergeDerivedWorkResult
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PostMergeDerivedWorkDispatcherTest {
    private val storyId = StoryId("story:post-merge")

    @Test
    fun requiredLocalReaggregationCompletesBeforeRequiredNetworkWorkIsScheduled() = runTest {
        val order = mutableListOf<String>()
        val result = dispatchPostMergeDerivedWork(
            storyId, PostMergeDerivedRequirements(true, true, true),
            reaggregate = { order += "reaggregate"; ChapterCommitResult.Success },
            scheduleNetworkWork = { _, mappings, sync -> order += "network:$mappings:$sync" },
        )
        assertEquals(PostMergeDerivedWorkResult.Dispatched, result)
        assertEquals(listOf("reaggregate", "network:true:true"), order)
    }

    @Test
    fun chapterOnlyDerivedWorkSkipsNetworkScheduling() = runTest {
        var reaggregated = false
        var scheduled = false
        val result = dispatchPostMergeDerivedWork(
            storyId, PostMergeDerivedRequirements(true, false, false),
            reaggregate = { reaggregated = true; ChapterCommitResult.Success },
            scheduleNetworkWork = { _, _, _ -> scheduled = true },
        )
        assertEquals(PostMergeDerivedWorkResult.Dispatched, result)
        assertEquals(true, reaggregated)
        assertEquals(false, scheduled)
    }

    @Test
    fun mappingOnlyDerivedWorkSkipsLocalReaggregationAndSchedulesOnlyMapping() = runTest {
        var reaggregated = false
        val scheduled = mutableListOf<Pair<Boolean, Boolean>>()
        val result = dispatchPostMergeDerivedWork(
            storyId, PostMergeDerivedRequirements(false, true, false),
            reaggregate = { reaggregated = true; ChapterCommitResult.Success },
            scheduleNetworkWork = { _, mappings, sync -> scheduled += mappings to sync },
        )
        assertEquals(PostMergeDerivedWorkResult.Dispatched, result)
        assertEquals(false, reaggregated)
        assertEquals(listOf(true to false), scheduled)
    }

    @Test
    fun syncOnlyDerivedWorkSchedulesOnlyChapterSync() = runTest {
        val scheduled = mutableListOf<Pair<Boolean, Boolean>>()
        val result = dispatchPostMergeDerivedWork(
            storyId, PostMergeDerivedRequirements(false, false, true),
            reaggregate = { error("must not run") },
            scheduleNetworkWork = { _, mappings, sync -> scheduled += mappings to sync },
        )
        assertEquals(PostMergeDerivedWorkResult.Dispatched, result)
        assertEquals(listOf(false to true), scheduled)
    }

    @Test
    fun localReaggregationFailureDoesNotScheduleNetworkWork() = runTest {
        var scheduled = false
        val result = dispatchPostMergeDerivedWork(
            storyId, PostMergeDerivedRequirements(true, true, true),
            reaggregate = { ChapterCommitResult.Failure("chapter.reaggregate.failed", retryable = true) },
            scheduleNetworkWork = { _, _, _ -> scheduled = true },
        )
        assertEquals(PostMergeDerivedWorkResult.Failed("chapter.reaggregate.failed", true), result)
        assertEquals(false, scheduled)
    }

    @Test
    fun reaggregationExceptionReturnsTransientDerivedFailureWithoutScheduling() = runTest {
        var scheduled = false
        val result = dispatchPostMergeDerivedWork(
            storyId, PostMergeDerivedRequirements(true, true, true),
            reaggregate = { error("Room temporarily unavailable") },
            scheduleNetworkWork = { _, _, _ -> scheduled = true },
        )
        assertEquals(PostMergeDerivedWorkResult.Failed("canonical.derived.reaggregation_exception", true), result)
        assertEquals(false, scheduled)
    }

    @Test
    fun schedulerFailureReturnsTransientDerivedFailure() = runTest {
        val result = dispatchPostMergeDerivedWork(
            storyId, PostMergeDerivedRequirements(false, true, false),
            reaggregate = { ChapterCommitResult.Success },
            scheduleNetworkWork = { _, _, _ -> error("WorkManager unavailable") },
        )
        assertEquals(PostMergeDerivedWorkResult.Failed("canonical.derived.schedule_failed", true), result)
    }
}
