package app.openstory.story.domain

import app.openstory.common.AppResult
import app.openstory.model.ContentType
import app.openstory.model.LanguageTag
import app.openstory.model.PluginId
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogImageReference
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogScore
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.catalog.CatalogSection
import app.openstory.plugin.host.HostedPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogDetailsMapperTest {
    @Test
    fun detailsMapToRichSourceMetadataWithHostedVersion() {
        val hosted = hostedCatalog(id = "catalog.a", version = "4.5.6")
        val details = CatalogDetails(
            sourceId = "story-1",
            sourceUrl = "https://catalog.example/story-1",
            title = "Fixture Novel",
            aliases = listOf("Alias"),
            authors = listOf("Author"),
            description = "Rich description",
            genres = listOf("Fantasy"),
            contentType = ContentType.WEB_NOVEL,
            languageTags = setOf("en"),
            image = CatalogImageReference(
                url = "https://images.example/cover.jpg",
                declaredHost = "images.example",
            ),
            score = CatalogScore(8.8, 10.0),
            popularityRank = 7,
        )

        val mapped = CatalogDetailsMapper().map(hosted, details)

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

private fun hostedCatalog(
    id: String,
    version: String,
): HostedPlugin<CatalogPlugin> = HostedPlugin(
    id = PluginId(id),
    version = version,
    instance = object : CatalogPlugin {
        override suspend fun home(request: CatalogHomeRequest): AppResult<List<CatalogSection>> =
            AppResult.Success(emptyList())

        override suspend fun search(request: CatalogSearchRequest): AppResult<Page<CatalogCard>> =
            AppResult.Success(Page(emptyList(), null))

        override suspend fun details(sourceId: String): AppResult<CatalogDetails> = error("Not used")

        override suspend fun filters(): AppResult<List<CatalogFilterDefinition>> = AppResult.Success(emptyList())
    },
)
