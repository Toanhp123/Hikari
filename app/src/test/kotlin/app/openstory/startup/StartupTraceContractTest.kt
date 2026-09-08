package app.openstory.startup

import org.junit.Assert.assertEquals
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
}
