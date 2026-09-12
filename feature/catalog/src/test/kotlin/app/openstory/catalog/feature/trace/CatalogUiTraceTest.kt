package app.openstory.catalog.feature.trace

import app.openstory.catalog.runtime.trace.CatalogTrace
import app.openstory.catalog.runtime.trace.CatalogTraceSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CatalogUiTraceTest {
    @Test
    fun recompositionAndMultipleCoverSuccessesEmitDiscoverMilestonesOnce() {
        val traces = mutableListOf<String>()
        val trace = CatalogUiTrace(CatalogTraceSink(traces::add))

        trace.discoverContentReady()
        trace.discoverContentReady()
        trace.discoverCoverReady()
        trace.discoverCoverReady()

        assertEquals(
            listOf(
                CatalogTrace.DISCOVER_CONTENT_READY,
                CatalogTrace.DISCOVER_FIRST_COVER,
            ),
            traces,
        )
    }

    @Test
    fun storyPublicationAndMaterializationMilestonesEmitOnceInBoundaryOrder() {
        val traces = mutableListOf<String>()
        val trace = CatalogUiTrace(CatalogTraceSink(traces::add))

        trace.storyHeroMaterialized()
        trace.storyUiPublished()
        trace.storyUiPublished()
        trace.storyHeroMaterialized()
        trace.storyHeroMaterialized()
        trace.storyBodyMaterialized()
        trace.storyBodyMaterialized()

        assertEquals(
            listOf(
                CatalogTrace.STORY_UI_PUBLISHED,
                CatalogTrace.STORY_HERO_MATERIALIZATION,
                CatalogTrace.STORY_BODY_MATERIALIZATION,
            ),
            traces,
        )
    }

    @Test
    fun spanControllerMapsActivationAndStoryRequestToTheirLatencyMilestones() {
        val controller = CatalogTraceSpanController()

        val activation = controller.onMark(CatalogTrace.ACTIVATION_START)
        val storage = controller.onMark(CatalogTrace.STORAGE_READY)
        val story = controller.onMark(CatalogTrace.STORY_DETAIL_REQUESTED)
        val storyReady = controller.onMark(CatalogTrace.STORY_DETAIL_CONTENT_READY)

        assertEquals(
            listOf(
                CatalogTrace.STORAGE_READY,
                CatalogTrace.DISCOVER_FIRST_SNAPSHOT,
                CatalogTrace.DISCOVER_FIRST_COVER,
                CatalogTrace.DISCOVER_CONTENT_READY,
            ),
            activation.started.map { it.name },
        )
        assertEquals(listOf(CatalogTrace.STORAGE_READY), storage.ended.map { it.name })
        assertEquals(listOf(CatalogTrace.STORY_DETAIL_CONTENT_READY), story.started.map { it.name })
        assertEquals(listOf(CatalogTrace.STORY_DETAIL_CONTENT_READY), storyReady.ended.map { it.name })
    }

    @Test
    fun repeatedStoryRequestEndsTheUnfinishedSpanAndCompletesOnlyTheReplacementCookie() {
        val controller = CatalogTraceSpanController()

        val firstRequest = controller.onMark(CatalogTrace.STORY_DETAIL_REQUESTED)
        val replacementRequest = controller.onMark(CatalogTrace.STORY_DETAIL_REQUESTED)
        val ready = controller.onMark(CatalogTrace.STORY_DETAIL_CONTENT_READY)
        val duplicateReady = controller.onMark(CatalogTrace.STORY_DETAIL_CONTENT_READY)

        val first = firstRequest.started.single()
        val replacement = replacementRequest.started.single()
        assertEquals(listOf(first), replacementRequest.ended)
        assertNotEquals(first.cookie, replacement.cookie)
        assertEquals(listOf(replacement), ready.ended)
        assertEquals(CatalogTraceSpanTransition(), duplicateReady)
    }
}
