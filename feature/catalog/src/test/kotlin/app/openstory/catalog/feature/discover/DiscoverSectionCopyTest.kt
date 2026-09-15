package app.openstory.catalog.feature.discover

import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.feature.R
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverSectionCopyTest {
    @Test
    fun latestUpdatesUsesTruthfulHikariCopy() {
        assertEquals(R.string.discover_section_latest_updates, CatalogSectionKind.LATEST_UPDATES.titleResource)
    }
}
