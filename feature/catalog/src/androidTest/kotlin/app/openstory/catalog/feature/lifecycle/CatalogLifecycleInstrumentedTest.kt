package app.openstory.catalog.feature.lifecycle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import app.openstory.catalog.feature.CatalogComposition
import app.openstory.catalog.feature.CatalogDebugDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CatalogLifecycleInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun resetDiagnostics() {
        CatalogDebugDiagnostics.reset()
    }

    @Test
    fun stopQuiescesCollectorsAndCoverDemandWithoutRefreshingOrChurningResources() {
        ActivityScenario.launch(CatalogLifecycleTestActivity::class.java).use { scenario ->
            waitFor("initial catalog activation") {
                CatalogDebugDiagnostics.activeDiscoverCollectorCount() == 1 &&
                    CatalogDebugDiagnostics.activeCoverDemandCount() > 0
            }
            val acquisitionCount = CatalogDebugDiagnostics.acquisitionStartCount()

            scenario.moveToState(Lifecycle.State.CREATED)
            waitFor("stopped catalog quiescence") {
                CatalogDebugDiagnostics.activeDiscoverCollectorCount() == 0 &&
                    CatalogDebugDiagnostics.activeCoverDemandCount() == 0
            }

            assertEquals(acquisitionCount, CatalogDebugDiagnostics.acquisitionStartCount())
            assertEquals(1, CatalogDebugDiagnostics.imageSessionInitializationCount())
            assertEquals(0, CatalogDebugDiagnostics.imageSessionCloseCount())
            assertEquals(0, CatalogDebugDiagnostics.runtimeSessionCloseCount())

            scenario.moveToState(Lifecycle.State.STARTED)
            waitFor("resumed persisted catalog") {
                CatalogDebugDiagnostics.activeDiscoverCollectorCount() == 1 &&
                    CatalogDebugDiagnostics.activeCoverDemandCount() > 0
            }

            assertEquals(acquisitionCount, CatalogDebugDiagnostics.acquisitionStartCount())
            assertEquals(1, CatalogDebugDiagnostics.imageSessionInitializationCount())
            assertEquals(0, CatalogDebugDiagnostics.imageSessionCloseCount())
            assertEquals(0, CatalogDebugDiagnostics.runtimeSessionCloseCount())
        }
    }

    @Test
    fun terminalActivityDestructionClosesImageThenRuntimeExactlyOnce() {
        val scenario = ActivityScenario.launch(CatalogLifecycleTestActivity::class.java)
        waitFor("initialized catalog resources") {
            CatalogDebugDiagnostics.imageSessionInitializationCount() == 1 &&
                CatalogDebugDiagnostics.storageReadyCount() == 1
        }

        scenario.close()
        waitFor("terminal catalog teardown") {
            CatalogDebugDiagnostics.imageSessionCloseCount() == 1 &&
                CatalogDebugDiagnostics.runtimeSessionCloseCount() == 1
        }

        assertEquals(1, CatalogDebugDiagnostics.imageSessionCloseCount())
        assertEquals(1, CatalogDebugDiagnostics.runtimeSessionCloseCount())
        assertTrue(
            CatalogDebugDiagnostics.imageSessionClosedOrder() <
                CatalogDebugDiagnostics.runtimeSessionClosedOrder(),
        )
    }

    private fun waitFor(description: String, condition: () -> Boolean) {
        composeRule.waitUntil(
            conditionDescription = description,
            timeoutMillis = 5_000,
            condition = condition,
        )
    }
}

class CatalogLifecycleTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CatalogComposition()
            }
        }
    }
}
