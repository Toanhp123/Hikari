package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
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
    fun projectionBoundaryJoinsHomeFeedWithCanonicalPresentation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = DiscoverProjectionPipeline(FixedAppDispatchers(dispatcher, dispatcher, dispatcher))
        val storyId = StoryId("story:one")

        val projected = pipeline.project(
            homes = listOf(snapshot(storyId)),
            projections = listOf(CatalogStoryProjection(storyId, "Canonical One", ContentType.MANGA, "canonical.jpg")),
            selectedContentType = ContentType.MANGA,
        )

        assertEquals(listOf("Canonical One"), projected.popular.map { it.title })
        assertEquals(false, projected.sourceEmpty)
    }
}

private fun snapshot(storyId: StoryId) = CatalogHomeSnapshot(
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
                    storyId = storyId,
                    title = "Raw Story One",
                    contentType = ContentType.MANGA,
                ),
            ),
            kind = CatalogFeedKind.POPULAR,
        ),
    ),
    refreshedAtEpochMillis = 1L,
)
