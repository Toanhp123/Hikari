package app.openstory.catalog.ranking

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class AggregateRankingTest {
    @Test
    fun aggregateRankingPreservesScoreScale() {
        val entry = CatalogEntry(
            StoryId("story"),
            PluginId("p"),
            "s",
            "Title",
            contentType = ContentType.MANGA,
            score = Score(8.0, 10.0),
        )
        val result = AggregateRanking()
            .rank(listOf(CatalogEntryWithStory(StoryId("story"), entry)))
            .single()
        assertEquals(0.8, result.orderingScore, absoluteTolerance = 0.000001)
        assertEquals(Score(8.0, 10.0), result.contributions.single().entry.score)
    }
    @Test
    fun tieBreakIsStableByStoryId() {
        fun entry(id: String) = CatalogEntry(
            StoryId(id),
            PluginId("p"),
            id,
            id,
            contentType = ContentType.MANGA,
            score = Score(8.0, 10.0),
        )
        val ranking = AggregateRanking()
        val first = ranking.rank(
            listOf(
                CatalogEntryWithStory(StoryId("story:b"), entry("b")),
                CatalogEntryWithStory(StoryId("story:a"), entry("a")),
            ),
        )
        assertEquals(listOf(StoryId("story:a"), StoryId("story:b")), first.map { it.storyId })
    }
}
