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
    fun completingSetupReachesHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually("startup-first-run")
            composeRule.onNodeWithTag("startup-complete").performClick()
            assertDisplayedEventually("startup-home")
        }
    }

    @Test
    fun completedSetupLaunchesHome() {
        markInitialSetupCompleted()

        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually("startup-home")
        }
    }

    @Test
    fun completedSetupSurvivesActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertDisplayedEventually("startup-first-run")
            composeRule.onNodeWithTag("startup-complete").performClick()
            assertDisplayedEventually("startup-home")

            scenario.recreate()

            assertDisplayedEventually("startup-home")
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
