package app.openstory.catalog.feature.discover

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogSectionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverViewportLayoutTest {
    @Test
    fun verticalSectionsExposeOneLazyRowPerCardWhilePopularRemainsOneCarousel() {
        val latestRows = DiscoverSectionUi(
            CatalogSectionKind.LATEST_UPDATES,
            List(9, ::card),
        ).viewportRows()
        val popularRows = DiscoverSectionUi(
            CatalogSectionKind.POPULAR,
            List(5, ::card),
        ).viewportRows()

        assertTrue(latestRows.first() is DiscoverViewportRow.Header)
        assertEquals(9, latestRows.filterIsInstance<DiscoverViewportRow.VerticalCard>().size)
        assertEquals(0, latestRows.filterIsInstance<DiscoverViewportRow.Carousel>().size)
        assertEquals(1, popularRows.filterIsInstance<DiscoverViewportRow.Carousel>().size)
        assertEquals(0, popularRows.filterIsInstance<DiscoverViewportRow.VerticalCard>().size)
    }

    private fun card(position: Int): DiscoverCardUi {
        val sourceStoryId = "viewport-$position"
        val sourceKey = SourceStoryKey(SOURCE_KEY, sourceStoryId)
        return DiscoverCardUi(
            ref = StorySourceRef(
                storyId = SourceStoryIdV1.derive(sourceKey),
                catalogSourceKey = SOURCE_KEY,
                sourceStoryId = sourceStoryId,
            ),
            title = "Story $position",
            coverAssetKey = null,
            ratingLabel = null,
            supportingLabel = null,
        )
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("discover-viewport-layout-test")
    }
}
