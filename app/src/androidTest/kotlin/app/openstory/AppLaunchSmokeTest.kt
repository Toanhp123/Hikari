package app.openstory

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesHomeAndNavigatesTopLevelDestinations() {
        composeRule.onNodeWithText("Home")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Search all stories")
            .assertIsDisplayed()

        composeRule.onNodeWithText("Discover")
            .performClick()
        composeRule.onNodeWithText("Discover")
            .assertIsDisplayed()

        composeRule.onNodeWithText("Library")
            .performClick()
        composeRule.onAllNodesWithText("Library")
            .assertCountEquals(2)
    }

    @Test
    fun transitionalHomeOpensCatalogSearchUntilDashboardTask() {
        composeRule.onNodeWithText("Refresh")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Search all stories")
            .performClick()
        composeRule.onNodeWithText("Search catalogs")
            .assertIsDisplayed()
    }

    @Test
    fun productionHomeDoesNotExposeFixtureCatalogCards() {
        composeRule.onAllNodesWithText("Hikari Chronicles")
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("JavaScript Lantern")
            .assertCountEquals(0)
    }

    @Test
    fun selectedTopLevelDestinationSurvivesActivityRecreation() {
        composeRule.onNodeWithText("Library")
            .performClick()
        composeRule.onAllNodesWithText("Library")
            .assertCountEquals(2)

        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Library")
                .fetchSemanticsNodes()
                .size == 2
        }
        composeRule.onAllNodesWithText("Library")
            .assertCountEquals(2)
    }
}
