package app.openstory.startup

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.openstory.MainActivity
import java.lang.reflect.Method
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CatalogLaunchHandoffTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun resetCatalogDiagnostics() {
        diagnosticsMethod("reset").invoke(null)
    }

    @Test
    fun beforeReadyCatalogDoesNoStorageImageOrAcquisitionWork() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually("startup-first-run")

            assertEquals(0, diagnosticCount("activationStartCount"))
            assertEquals(0, diagnosticCount("storageReadyCount"))
            assertEquals(0, diagnosticCount("acquisitionStartCount"))
            assertEquals(0, diagnosticCount("imageLoaderInitializationCount"))
        }
    }

    @Test
    fun returningReadyLaunchDisplaysFeatureOwnedDiscoverRoot() {
        markInitialSetupCompleted()

        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually("catalog-discover")
            waitForDiagnosticCount("storageReadyCount", 1)

            assertEquals(1, diagnosticCount("activationStartCount"))
            assertEquals(1, diagnosticCount("storageReadyCount"))
        }
    }

    @Test
    fun firstRunCompletionPersistsBeforeCatalogActivation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually("startup-first-run")
            assertEquals(0, diagnosticCount("activationStartCount"))

            composeRule.onNodeWithTag("startup-complete").performClick()
            assertDisplayedEventually("catalog-discover")
            waitForDiagnosticCount("activationStartCount", 1)

            assertEquals(AppLaunchState.Ready, resolveLaunchState())
            assertTrue(diagnosticCount("activationStartCount") > 0)
        }
    }

    private fun assertDisplayedEventually(tag: String) {
        val node = composeRule.onNodeWithTag(tag)
        composeRule.waitUntil(
            conditionDescription = "$tag is displayed",
            timeoutMillis = 5_000,
            condition = node::isDisplayed,
        )
        node.assertIsDisplayed()
    }

    private fun waitForDiagnosticCount(methodName: String, minimum: Int) {
        composeRule.waitUntil(
            conditionDescription = "$methodName reaches $minimum",
            timeoutMillis = 5_000,
        ) {
            diagnosticCount(methodName) >= minimum
        }
    }

    private fun diagnosticCount(methodName: String): Int =
        diagnosticsMethod(methodName).invoke(null) as Int

    private fun diagnosticsMethod(name: String): Method = Class.forName(
        "app.openstory.catalog.feature.CatalogDebugDiagnostics",
    ).getMethod(name)

    private fun markInitialSetupCompleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            check(createAppLaunchStateStore(context).markInitialSetupCompleted())
        }
    }

    private fun resolveLaunchState(): AppLaunchState {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return runBlocking { createAppLaunchStateStore(context).resolve() }
    }
}
