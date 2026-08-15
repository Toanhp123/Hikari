package app.openstory.catalog.home

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogHomeQueryTest {
    @Test
    fun queryRanksCachedHomesAndDeduplicatesSectionMembership() {
        val entry = CatalogEntry(
            StoryId("story"),
            PluginId("p"),
            "s",
            "Title",
            contentType = ContentType.MANGA,
            score = Score(8.0, 10.0),
        )
        val home = CatalogHomeSnapshot(
            PluginId("p"),
            "1",
            1,
            listOf(
                CatalogHomeSection("popular", "Popular", listOf(entry)),
                CatalogHomeSection("seasonal", "Seasonal", listOf(entry)),
            ),
        )

        val ranked = CatalogHomeQuery().rank(listOf(home)).single()

        assertEquals(StoryId("story"), ranked.storyId)
        assertEquals(1, ranked.contributions.size)
    }
}
