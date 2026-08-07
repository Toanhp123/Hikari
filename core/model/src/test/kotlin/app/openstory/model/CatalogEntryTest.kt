package app.openstory.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogEntryTest {
    @Test
    fun catalogMetadataIsRetainedWithoutFlatteningSourceValues() {
        val entry = CatalogEntry(
            id = CatalogEntryId("catalog:story-1"),
            catalogPluginId = PluginId("catalog.example"),
            externalStoryId = "story-1",
            sourceUrl = "https://catalog.example/story-1",
            title = "Example",
            authors = linkedSetOf("Author A", "Author B"),
            description = "Description",
            genres = linkedSetOf("Fantasy", "Adventure"),
            coverReference = "https://catalog.example/cover.jpg",
            publicationStatus = "ONGOING",
            score = 8.4,
            scoreScale = 10.0,
        )

        assertEquals("story-1", entry.externalStoryId)
        assertEquals(setOf("Author A", "Author B"), entry.authors)
        assertEquals(setOf("Fantasy", "Adventure"), entry.genres)
        assertEquals("ONGOING", entry.publicationStatus)
    }

    @Test
    fun blankExternalStoryIdIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            CatalogEntry(
                id = CatalogEntryId("catalog:story-1"),
                catalogPluginId = PluginId("catalog.example"),
                externalStoryId = " ",
                sourceUrl = null,
                title = "Example",
                authors = emptySet(),
                description = null,
                genres = emptySet(),
                coverReference = null,
                publicationStatus = null,
                score = null,
                scoreScale = null,
            )
        }
    }
}
