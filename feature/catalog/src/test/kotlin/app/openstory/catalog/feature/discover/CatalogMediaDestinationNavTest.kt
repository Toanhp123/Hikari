package app.openstory.catalog.feature.discover

import app.openstory.catalog.domain.model.CatalogMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogMediaDestinationNavTest {
    @Test
    fun selectedMediaIsNoOpAndOtherMediaProducesOneSelection() {
        assertNull(
            DiscoverNavTab.MANGA.mediaSelectionFrom(CatalogMediaType.MANGA),
        )
        assertEquals(
            CatalogMediaType.LIGHT_NOVEL,
            DiscoverNavTab.LIGHT_NOVEL.mediaSelectionFrom(CatalogMediaType.MANGA),
        )
    }

    @Test
    fun homeNeverMutatesMediaSelection() {
        assertNull(
            DiscoverNavTab.HOME.mediaSelectionFrom(CatalogMediaType.LIGHT_NOVEL),
        )
    }
}
