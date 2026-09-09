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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class StartupFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun freshPackageReachesFirstRun() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually("startup-first-run")
        }
    }

    @Test
    fun completingSetupReachesCatalog() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually("startup-first-run")
            composeRule.onNodeWithTag("startup-complete").performClick()
            assertDisplayedEventually("catalog-discover")
        }
    }

    @Test
    fun completedSetupLaunchesCatalog() {
        markInitialSetupCompleted()

        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually("catalog-discover")
        }
    }

    @Test
    fun completedSetupSurvivesActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertDisplayedEventually("startup-first-run")
            composeRule.onNodeWithTag("startup-complete").performClick()
            assertDisplayedEventually("catalog-discover")

            scenario.recreate()

            assertDisplayedEventually("catalog-discover")
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

    private fun markInitialSetupCompleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            check(createAppLaunchStateStore(context).markInitialSetupCompleted())
        }
    }
}
