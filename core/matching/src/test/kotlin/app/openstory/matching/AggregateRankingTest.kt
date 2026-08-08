package app.openstory.matching

import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryId
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.ContentType
import app.openstory.model.PluginId
import app.openstory.model.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AggregateRankingTest {
    @Test
    fun rankingNormalizesScoresWeightsPrioritiesAndPreservesSourceEntries() {
        val highPriority = entry("catalog.a", "a-1", score = 80.0, scale = 100.0)
        val lowPriority = entry("catalog.b", "b-1", score = 9.0, scale = 10.0)
        val ranking = AggregateRanking(
            catalogWeights = mapOf(
                PluginId("catalog.a") to 2.0,
                PluginId("catalog.b") to 1.0,
            ),
        )

        val ranked = ranking.rank(
            listOf(
                CatalogEntryWithStory(StoryId("story-1"), highPriority),
                CatalogEntryWithStory(StoryId("story-1"), lowPriority),
            ),
        ).single()

        assertEquals(StoryId("story-1"), ranked.storyId)
        assertEquals((0.8 * 2.0 + 0.9) / 3.0, ranked.orderingScore, absoluteTolerance = 0.000001)
        assertEquals(listOf("catalog.a", "catalog.b"), ranked.contributions.map { it.entry.catalogPluginId.value })
        assertSame(highPriority, ranked.contributions[0].entry)
        assertSame(lowPriority, ranked.contributions[1].entry)
        assertEquals(80.0, ranked.contributions[0].entry.score)
        assertEquals(100.0, ranked.contributions[0].entry.scoreScale)
    }

    @Test
    fun rankingIsIndependentOfInputOrderAndUsesStableStoryIdTieBreaker() {
        val items = listOf(
            CatalogEntryWithStory(StoryId("story-b"), entry("catalog.a", "b", 8.0, 10.0)),
            CatalogEntryWithStory(StoryId("story-a"), entry("catalog.a", "a", 8.0, 10.0)),
        )
        val ranking = AggregateRanking()

        val first = ranking.rank(items).map { it.storyId }
        val second = ranking.rank(items.reversed()).map { it.storyId }

        assertEquals(listOf(StoryId("story-a"), StoryId("story-b")), first)
        assertEquals(first, second)
    }

    @Test
    fun missingScoresAreNeutralInsteadOfInvented() {
        val scored = entry("catalog.a", "scored", 7.5, 10.0)
        val unscored = entry("catalog.b", "unscored", null, null)
        val ranked = AggregateRanking().rank(
            listOf(
                CatalogEntryWithStory(StoryId("story"), unscored),
                CatalogEntryWithStory(StoryId("story"), scored),
            ),
        ).single()

        assertEquals(0.75, ranked.orderingScore, absoluteTolerance = 0.000001)
        assertEquals(null, ranked.contributions.single { it.entry === unscored }.normalizedScore)
    }

    private fun entry(
        pluginId: String,
        sourceId: String,
        score: Double?,
        scale: Double?,
    ) = CatalogEntry(
        id = CatalogEntryId("$pluginId:$sourceId"),
        catalogPluginId = PluginId(pluginId),
        externalStoryId = sourceId,
        sourceUrl = null,
        title = sourceId,
        aliases = emptySet(),
        authors = emptySet(),
        description = null,
        genres = emptySet(),
        contentType = ContentType.WEB_NOVEL,
        languageTags = emptySet(),
        coverReference = null,
        publicationStatus = null,
        score = score,
        scoreScale = scale,
        popularityRank = null,
        pluginVersion = "1.0.0",
        fetchedAtEpochMillis = 1L,
    )
}
