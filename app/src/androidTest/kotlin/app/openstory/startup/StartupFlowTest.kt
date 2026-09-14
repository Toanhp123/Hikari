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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class StartupFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun freshPackageReachesFirstRun() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually(FIRST_RUN_TAG)
        }
    }

    @Test
    fun completingSetupReachesHomeWithoutComposingDiscover() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually(FIRST_RUN_TAG)
            composeRule.onNodeWithTag(COMPLETE_SETUP_TAG).performClick()
            assertDisplayedEventually(AppShellTestTags.HOME_ROOT)
            composeRule.onNodeWithTag(DISCOVER_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun completedSetupLaunchesHomeWithoutComposingDiscover() {
        markInitialSetupCompleted()

        ActivityScenario.launch(MainActivity::class.java).use {
            assertDisplayedEventually(AppShellTestTags.HOME_ROOT)
            composeRule.onNodeWithTag(DISCOVER_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun completedSetupSurvivesActivityRecreationAtHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertDisplayedEventually(FIRST_RUN_TAG)
            composeRule.onNodeWithTag(COMPLETE_SETUP_TAG).performClick()
            assertDisplayedEventually(AppShellTestTags.HOME_ROOT)
            composeRule.onNodeWithTag(DISCOVER_TAG).assertDoesNotExist()

            scenario.recreate()

            assertDisplayedEventually(AppShellTestTags.HOME_ROOT)
            composeRule.onNodeWithTag(DISCOVER_TAG).assertDoesNotExist()
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

    private companion object {
        const val FIRST_RUN_TAG = "startup-first-run"
        const val COMPLETE_SETUP_TAG = "startup-complete"
        const val DISCOVER_TAG = "catalog-discover"
    }
}
