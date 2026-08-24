package app.openstory.catalog.ui.discover

import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.common.id.StoryId
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoverCanonicalBootstrapPipelineTest {
    @Test
    fun oneBrokenStoryDoesNotBlockRemainingPriorityStories() = runTest {
        val storyIds = listOf(StoryId("story:1"), StoryId("story:2"), StoryId("story:3"))
        val canonical = DiscoverCanonicalRepository(storyIds.map(::preparingDiscoverState))
        val calls = mutableListOf<StoryId>()
        val bootstrap = CanonicalBootstrapUseCase(
            canonical,
            CanonicalGenerationRebuilder { storyId, _ ->
                calls += storyId
                if (storyId == StoryId("story:2")) error("broken local evidence")
                CanonicalFusionResult.Preparing(storyId)
            },
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = DiscoverCanonicalBootstrapPipeline(
            bootstrap,
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )

        pipeline.prewarm(storyIds)

        assertEquals(storyIds, calls)
    }
}
