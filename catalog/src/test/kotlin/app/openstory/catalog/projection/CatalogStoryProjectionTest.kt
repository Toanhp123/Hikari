package app.openstory.catalog.projection

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogStoryProjectionTest {
    @Test
    fun projectionUsesDeterministicCatalogSourceOrder() {
        val story = Story(StoryId("story-1"), ContentType.WEB_NOVEL)
        val projection = projectCatalogStory(
            story,
            listOf(
                entry("catalog.b", "source-1", "Second"),
                entry("catalog.a", "source-2", "Preferred"),
            ),
        )

        assertEquals("Preferred", projection.title)
        assertEquals(StoryId("story-1"), projection.storyId)
    }

    @Test
    fun projectionFallsBackToStableStoryIdWithoutCatalogEntry() {
        val story = Story(StoryId("story-orphan"), ContentType.MANGA)

        assertEquals("story-orphan", projectCatalogStory(story, emptyList()).title)
    }

    private fun entry(pluginId: String, sourceId: String, title: String) = CatalogEntry(
        storyId = StoryId("story-1"),
        pluginId = PluginId(pluginId),
        sourceId = sourceId,
        title = title,
        contentType = ContentType.WEB_NOVEL,
    )
}
