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
import app.openstory.composition.AppShellTestTags
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
            assertDisplayedEventually(FIRST_RUN_TAG)

            assertNoCatalogWork()
        }
    }

    @Test
    fun returningReadyLaunchDefersCatalogUntilUserExploresManga() {
        markInitialSetupCompleted()

        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually(AppShellTestTags.HOME_ROOT)
            composeRule.onNodeWithTag(DISCOVER_TAG).assertDoesNotExist()
            assertNoCatalogWork()

            composeRule.onNodeWithTag(AppShellTestTags.EXPLORE_MANGA).performClick()
            assertDisplayedEventually(DISCOVER_TAG)
            waitForDiagnosticCount("activationStartCount", 1)
            waitForDiagnosticCount("storageReadyCount", 1)

            assertEquals(1, diagnosticCount("activationStartCount"))
            assertEquals(1, diagnosticCount("storageReadyCount"))
        }
    }

    @Test
    fun firstRunCompletionPersistsReadyBeforeCatalogActivation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually(FIRST_RUN_TAG)
            assertNoCatalogWork()

            composeRule.onNodeWithTag(COMPLETE_SETUP_TAG).performClick()
            assertDisplayedEventually(AppShellTestTags.HOME_ROOT)
            composeRule.onNodeWithTag(DISCOVER_TAG).assertDoesNotExist()

            assertEquals(AppLaunchState.Ready, resolveLaunchState())
            assertNoCatalogWork()

            composeRule.onNodeWithTag(AppShellTestTags.EXPLORE_MANGA).performClick()
            assertDisplayedEventually(DISCOVER_TAG)
            waitForDiagnosticCount("activationStartCount", 1)

            assertTrue(diagnosticCount("activationStartCount") > 0)
        }
    }

    private fun assertNoCatalogWork() {
        composeRule.waitForIdle()
        assertEquals(0, diagnosticCount("activationStartCount"))
        assertEquals(0, diagnosticCount("storageReadyCount"))
        assertEquals(0, diagnosticCount("acquisitionStartCount"))
        assertEquals(0, diagnosticCount("imageLoaderInitializationCount"))
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

    private companion object {
        const val FIRST_RUN_TAG = "startup-first-run"
        const val COMPLETE_SETUP_TAG = "startup-complete"
        const val DISCOVER_TAG = "catalog-discover"
    }
}
