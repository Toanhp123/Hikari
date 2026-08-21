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
    fun projectionCurrentlyUsesFirstSortedCatalogSource() {
        // Characterization only: Phase 2 replaces this with CanonicalGeneration policy.
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
    fun projectionAggregatesAliasesAndAuthorsForLibraryMatching() {
        val story = Story(StoryId("story-1"), ContentType.WEB_NOVEL)
        val projection = projectCatalogStory(
            story,
            listOf(
                entry("catalog.b", "source-1", "Second", setOf("Alias B"), setOf("Author B")),
                entry("catalog.a", "source-2", "Preferred", setOf("Alias A"), setOf("Author A")),
            ),
        )

        assertEquals(setOf("Alias A", "Alias B"), projection.aliases)
        assertEquals(setOf("Author A", "Author B"), projection.authors)
    }

    @Test
    fun projectionFallsBackToStableStoryIdWithoutCatalogEntry() {
        val story = Story(StoryId("story-orphan"), ContentType.MANGA)

        assertEquals("story-orphan", projectCatalogStory(story, emptyList()).title)
    }

    private fun entry(
        pluginId: String,
        sourceId: String,
        title: String,
        aliases: Set<String> = emptySet(),
        authors: Set<String> = emptySet(),
    ) = CatalogEntry(
        storyId = StoryId("story-1"),
        pluginId = PluginId(pluginId),
        sourceId = sourceId,
        title = title,
        aliases = aliases,
        authors = authors,
        contentType = ContentType.WEB_NOVEL,
    )
}
