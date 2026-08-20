package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverProjectionPipelineTest {
    @Test
    fun oneHomeSnapshotProducesSemanticStateOnProjectionBoundary() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = DiscoverProjectionPipeline(
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )
        val homes = listOf(snapshot())

        val projected = pipeline.project(
            homes = homes,
            selectedContentType = ContentType.MANGA,
        )

        assertEquals(listOf(StoryId("story:one")), projected.popular.map(DiscoverStoryItem::storyId))
        assertEquals(ContentType.MANGA, projected.selectedContentType)
        assertEquals(false, projected.sourceEmpty)
    }
}

private fun snapshot() = CatalogHomeSnapshot(
    pluginId = PluginId("catalog.one"),
    pluginVersion = "1.0.0",
    sections = listOf(
        CatalogHomeSection(
            sourceId = "popular",
            title = "Popular",
            items = listOf(
                CatalogEntry(
                    pluginId = PluginId("catalog.one"),
                    sourceId = "entry:one",
                    storyId = StoryId("story:one"),
                    title = "Story One",
                    contentType = ContentType.MANGA,
                ),
            ),
            kind = CatalogFeedKind.POPULAR,
        ),
    ),
    refreshedAtEpochMillis = 1L,
)
