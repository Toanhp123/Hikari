package app.openstory.story.domain

import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.model.ContentType
import app.openstory.model.LanguageTag
import app.openstory.model.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogDetailsMapperTest {
    @Test
    fun detailsMapToRichSourceMetadataWithSourceVersion() {
        val source = UnusedCatalogSource(PluginId("catalog.a"), "4.5.6")
        val details = SourceDetails(
            sourceId = "story-1",
            sourceUrl = "https://catalog.example/story-1",
            title = "Fixture Novel",
            aliases = setOf("Alias"),
            authors = setOf("Author"),
            description = "Rich description",
            genres = setOf("Fantasy"),
            contentType = SourceContentType.WEB_NOVEL,
            languageTags = setOf("en"),
            coverUrl = "https://images.example/cover.jpg",
            scoreValue = 8.8,
            scoreScale = 10.0,
            popularityRank = 7,
        )

        val mapped = CatalogDetailsMapper().map(source, details)

        assertEquals(PluginId("catalog.a"), mapped.pluginId)
        assertEquals("4.5.6", mapped.pluginVersion)
        assertEquals("story-1", mapped.metadata.sourceId)
        assertEquals("https://catalog.example/story-1", mapped.metadata.sourceUrl)
        assertEquals(setOf("Alias"), mapped.metadata.aliases)
        assertEquals(setOf("Author"), mapped.metadata.authors)
        assertEquals(setOf("Fantasy"), mapped.metadata.genres)
        assertEquals(ContentType.WEB_NOVEL, mapped.metadata.contentType)
        assertEquals(setOf(LanguageTag("en")), mapped.metadata.languageTags)
        assertEquals("https://images.example/cover.jpg", mapped.metadata.coverReference)
        assertEquals(8.8, mapped.metadata.score)
        assertEquals(10.0, mapped.metadata.scoreScale)
        assertEquals(7, mapped.metadata.popularityRank)
        assertNull(mapped.metadata.publicationStatus)
    }
}

private class UnusedCatalogSource(
    override val pluginId: PluginId,
    override val version: String,
) : CatalogSource {
    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> = error("Not used")
    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> = error("Not used")
    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> = error("Not used")
    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("Not used")
}
