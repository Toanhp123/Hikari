package app.openstory.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupTraceContractTest {
    @Test
    fun startupTraceLabelsAreStableAndUnique() {
        assertEquals(
            setOf(
                "HikariV2:application-created",
                "HikariV2:activity-created",
                "HikariV2:content-requested",
                "HikariV2:first-frame",
                "HikariV2:launch-state-resolved",
                "HikariV2:destination-ready",
            ),
            startupTraceLabels.toSet(),
        )
        assertEquals(startupTraceLabels.size, startupTraceLabels.toSet().size)
    }

    @Test
    fun catalogActivationAddsItsOwnTraceWithoutChangingStartupMilestones() {
        val traceClass = Class.forName("app.openstory.catalog.runtime.trace.CatalogTrace")
        @Suppress("UNCHECKED_CAST")
        val catalogLabels = traceClass.getField("labels").get(null) as List<String>

        assertEquals(
            setOf(
                "HikariV2:catalog-activation-start",
                "HikariV2:catalog-storage-ready",
                "HikariV2:discover-first-snapshot",
                "HikariV2:discover-first-cover",
                "HikariV2:discover-content-ready",
                "HikariV2:story-detail-requested",
                "HikariV2:story-detail-content-ready",
            ),
            catalogLabels.toSet(),
        )
        assertEquals(catalogLabels.size, catalogLabels.toSet().size)
        assertTrue(startupTraceLabels.none(catalogLabels::contains))
    }
}
