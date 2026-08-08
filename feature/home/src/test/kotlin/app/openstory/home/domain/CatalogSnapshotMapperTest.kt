package app.openstory.home.domain

import app.openstory.common.AppResult
import app.openstory.model.ContentType
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

class CatalogSnapshotMapperTest {
    @Test
    fun hostedVersionAndCardContentTypeSurviveNormalization() {
        val hosted: HostedPlugin<CatalogPlugin> = HostedPlugin(
            id = PluginId("catalog.a"),
            version = "2.3.4",
            instance = UnusedCatalogPlugin,
        )
        val sections = listOf(
            CatalogSection(
                sourceId = "trending",
                title = "Trending",
                items = listOf(
                    CatalogCard(
                        sourceId = "story-1",
                        title = "Story One",
                        contentType = ContentType.WEB_NOVEL,
                        authors = listOf("Author"),
                        image = CatalogImageReference(
                            url = "https://catalog.example/cover.jpg",
                            declaredHost = "catalog.example",
                        ),
                        score = CatalogScore(value = 8.4, scale = 10.0),
                    ),
                ),
            ),
        )

        val snapshot = CatalogSnapshotMapper().map(hosted, sections)

        assertEquals(PluginId("catalog.a"), snapshot.pluginId)
        assertEquals("2.3.4", snapshot.pluginVersion)
        val item = snapshot.sections.single().items.single()
        assertEquals(ContentType.WEB_NOVEL, item.contentType)
        assertEquals("https://catalog.example/cover.jpg", item.coverReference)
        assertEquals(8.4, item.score)
        assertEquals(10.0, item.scoreScale)
    }
}

private object UnusedCatalogPlugin : CatalogPlugin {
    override suspend fun home(request: CatalogHomeRequest) = AppResult.Success(emptyList<CatalogSection>())
    override suspend fun search(request: CatalogSearchRequest) = AppResult.Success(Page<CatalogCard>(emptyList(), null))
    override suspend fun details(sourceId: String): AppResult<CatalogDetails> = error("Not used")
    override suspend fun filters(): AppResult<List<CatalogFilterDefinition>> = AppResult.Success(emptyList())
}
