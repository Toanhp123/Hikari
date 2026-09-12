package app.openstory.catalog.runtime.trace

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogTraceTest {
    @Test
    fun labelsExposeTheFrozenCatalogBenchmarkAuthorityInJourneyOrder() {
        assertEquals(
            listOf(
                "HikariV2:catalog-activation-start",
                "HikariV2:catalog-storage-ready",
                "HikariV2:discover-first-snapshot",
                "HikariV2:discover-first-cover",
                "HikariV2:discover-content-ready",
                "HikariV2:story-detail-requested",
                "HikariV2:story-detail-content-ready",
            ),
            CatalogTrace.labels,
        )
    }
}
