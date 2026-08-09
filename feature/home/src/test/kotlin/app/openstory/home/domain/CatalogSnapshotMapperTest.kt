package app.openstory.home.domain

import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceItem
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.model.ContentType
import app.openstory.model.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogSnapshotMapperTest {
    @Test
    fun sourceVersionAndItemContentTypeSurviveNormalization() {
        val source = UnusedCatalogSource(PluginId("catalog.a"), "2.3.4")
        val sections = listOf(
            SourceSection(
                sourceId = "trending",
                title = "Trending",
                items = listOf(
                    SourceItem(
                        sourceId = "story-1",
                        title = "Story One",
                        contentType = SourceContentType.WEB_NOVEL,
                        authors = setOf("Author"),
                        coverUrl = "https://catalog.example/cover.jpg",
                        scoreValue = 8.4,
                        scoreScale = 10.0,
                    ),
                ),
            ),
        )

        val snapshot = CatalogSnapshotMapper().map(source, sections)

        assertEquals(PluginId("catalog.a"), snapshot.pluginId)
        assertEquals("2.3.4", snapshot.pluginVersion)
        val item = snapshot.sections.single().items.single()
        assertEquals(ContentType.WEB_NOVEL, item.contentType)
        assertEquals("https://catalog.example/cover.jpg", item.coverReference)
        assertEquals(8.4, item.score)
        assertEquals(10.0, item.scoreScale)
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
