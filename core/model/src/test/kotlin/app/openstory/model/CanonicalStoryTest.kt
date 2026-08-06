package app.openstory.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanonicalStoryTest {

    @Test
    fun storyRetainsSeparateCatalogScores() {
        val story = CanonicalStory(
            id = StoryId("s1"),
            contentType = ContentType.WEB_NOVEL,
            preferredTitle = "Example",
            aliases = emptySet(),
            catalogEntries = listOf(
                CatalogEntry(
                    id = CatalogEntryId("mal:1"),
                    catalogPluginId = PluginId("mal"),
                    title = "Example",
                    description = null,
                    score = 8.4,
                    scoreScale = 10.0,
                ),
                CatalogEntry(
                    id = CatalogEntryId("ani:1"),
                    catalogPluginId = PluginId("ani"),
                    title = "Example",
                    description = null,
                    score = 84.0,
                    scoreScale = 100.0,
                ),
            ),
        )

        assertEquals(
            listOf(10.0, 100.0),
            story.catalogEntries.map { entry ->
                entry.scoreScale
            },
        )
    }

    @Test
    fun storyCanExistWithoutCatalogEntries() {
        val story = CanonicalStory(
            id = StoryId("manual:1"),
            contentType = ContentType.LIGHT_NOVEL,
            preferredTitle = "Manual Story",
            aliases = emptySet(),
            catalogEntries = emptyList(),
        )

        assertTrue(story.catalogEntries.isEmpty())
    }

    @Test
    fun libraryStateExistsWithoutReadableContentMapping() {
        val entry = LibraryEntry(
            storyId = StoryId("manual:1"),
            status = LibraryStatus.WANT_TO_READ,
            addedAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )

        assertEquals(
            StoryId("manual:1"),
            entry.storyId,
        )
        assertEquals(
            LibraryStatus.WANT_TO_READ,
            entry.status,
        )
    }
}
