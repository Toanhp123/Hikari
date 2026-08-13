package app.openstory

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
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
        composeRule.onNode(hasText("Home") and hasClickAction()).assertIsSelected()

        composeRule.onNode(hasText("Discover") and hasClickAction())
            .performClick()
        composeRule.onNodeWithContentDescription("Search all stories")
            .assertIsDisplayed()

        composeRule.onNode(hasText("Library") and hasClickAction())
            .performClick()
        composeRule.onNode(hasText("Library") and isHeading()).assertIsDisplayed()
    }

    @Test
    fun homeUtilityFocusMovesToFirstHomeAction() {
        val utility = composeRule.onNodeWithContentDescription("Open quick access")
        val firstHomeAction = composeRule.onNodeWithText("Discover stories")

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.window.decorView.requestFocusFromTouch()
        }
        utility.performSemanticsAction(SemanticsActions.RequestFocus)
        utility.assertIsFocused()
        utility.performKeyInput { pressKey(Key.DirectionDown) }

        firstHomeAction.assertIsFocused()
    }

    @Test
    fun discoverOpensCatalogSearch() {
        composeRule.onNode(hasText("Discover") and hasClickAction())
            .performClick()
        composeRule.onNodeWithContentDescription("Search all stories")
            .performClick()
        composeRule.onNodeWithText("Search every catalog")
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
        composeRule.onNode(hasText("Library") and hasClickAction())
            .performClick()
        composeRule.onNode(hasText("Library") and isHeading()).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Library") and isHeading())
                .fetchSemanticsNodes()
                .size == 1
        }
        composeRule.onNode(hasText("Library") and isHeading()).assertIsDisplayed()
    }
}
