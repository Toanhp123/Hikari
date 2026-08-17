package app.openstory.catalog.ui.discover

import app.openstory.catalog.home.CatalogHomeQuery
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverProjectionPipelineTest {
    @Test
    fun oneHomeSnapshotProducesRankingAndProjectedStateTogether() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = DiscoverProjectionPipeline(
            CatalogHomeQuery(),
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )
        val homes = listOf(snapshot())

        val prepared = pipeline.prepare(homes)
        val projected = pipeline.project(prepared, null, null, false, null)

        assertEquals(homes, prepared.catalogs)
        assertEquals(listOf(StoryId("story:one")), prepared.rankedStories.map { it.storyId })
        assertEquals("Story One", projected.featured?.title)
        assertEquals(
            listOf("combined:ranked", "catalog.one:popular"),
            projected.shelves.map(DiscoverShelf::key),
        )
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
                    contentType = ContentType.WEB_NOVEL,
                ),
            ),
        ),
    ),
    refreshedAtEpochMillis = 1L,
)
