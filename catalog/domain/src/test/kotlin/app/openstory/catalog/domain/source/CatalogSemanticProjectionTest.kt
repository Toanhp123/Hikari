package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionKind
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogSemanticProjectionTest {
    private val source = CatalogSourceKey("source")

    @Test
    fun popularPreservesSourceOrderAndCapsAtFive() {
        val items = (1..7).map { item("story-$it") }

        assertEquals(
            listOf("story-1", "story-2", "story-3", "story-4", "story-5"),
            CatalogSemanticProjection.project(source, CatalogSectionKind.POPULAR, items)
                .map { it.sourceStoryId },
        )
    }

    @Test
    fun latestUpdatesExcludesMissingTimestampsAndOrdersNewestFirstStably() {
        val items = listOf(
            item("older", latestUpdateEpochMs = 10),
            item("same-first", latestUpdateEpochMs = 30),
            item("missing", latestUpdateEpochMs = null),
            item("newest", latestUpdateEpochMs = 40),
            item("same-second", latestUpdateEpochMs = 30),
        )

        assertEquals(
            listOf("newest", "same-first", "same-second", "older"),
            CatalogSemanticProjection.project(source, CatalogSectionKind.LATEST_UPDATES, items)
                .map { it.sourceStoryId },
        )
    }

    @Test
    fun topRatedUsesNormalizedScoreAndPreservesOriginalRatingScale() {
        val items = listOf(
            item("eight-of-ten", rating = CatalogRating(8.0, 10.0)),
            item("four-of-five", rating = CatalogRating(4.0, 5.0)),
            item("nine-of-ten", rating = CatalogRating(9.0, 10.0)),
            item("missing", rating = null),
            item("nan", rating = CatalogRating(Double.NaN, 10.0)),
            item("bad-scale", rating = CatalogRating(1.0, 0.0)),
            item("over-scale", rating = CatalogRating(11.0, 10.0)),
        )

        val projected = CatalogSemanticProjection.project(source, CatalogSectionKind.TOP_RATED, items)

        assertEquals(listOf("nine-of-ten", "eight-of-ten", "four-of-five"), projected.map { it.sourceStoryId })
        assertEquals(CatalogRating(8.0, 10.0), projected[1].rating)
        assertEquals(CatalogRating(4.0, 5.0), projected[2].rating)
    }

    @Test
    fun latestAndTopRatedUseTheirExactNineAndFiveCaps() {
        val latest = (1..12).map { index -> item("latest-$index", latestUpdateEpochMs = index.toLong()) }
        val rated = (1..8).map { index -> item("rated-$index", rating = CatalogRating(index.toDouble(), 10.0)) }

        assertEquals(9, CatalogSemanticProjection.project(source, CatalogSectionKind.LATEST_UPDATES, latest).size)
        assertEquals(5, CatalogSemanticProjection.project(source, CatalogSectionKind.TOP_RATED, rated).size)
    }

    private fun item(
        sourceStoryId: String,
        latestUpdateEpochMs: Long? = 1,
        rating: CatalogRating? = CatalogRating(1.0, 10.0),
    ) = DiscoverAcquisitionItem(
        sourceStoryId = sourceStoryId,
        title = sourceStoryId,
        contentType = CatalogMediaType.MANGA,
        cover = null,
        rating = rating,
        publicationStatusSummary = null,
        latestUpdateEpochMs = latestUpdateEpochMs,
    )
}
