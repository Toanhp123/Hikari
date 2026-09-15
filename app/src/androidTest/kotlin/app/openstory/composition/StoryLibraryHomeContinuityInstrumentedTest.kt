package app.openstory.composition

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.openstory.MainActivity
import app.openstory.library.feature.HomeTestTags
import app.openstory.startup.createAppLaunchStateStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class StoryLibraryHomeContinuityInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun addFromDiscoverAppearsOnHomeAndRemoveFromStoryDisappearsAfterBack() {
        markInitialSetupCompleted()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForTag(HomeTestTags.ROOT)
            composeRule.onNodeWithTag(HomeTestTags.EXPLORE_MANGA).performClick()
            waitForText(STORY_TITLE)
            composeRule.onAllNodesWithText(STORY_TITLE).onFirst().performClick()

            waitForText("Add to Library")
            composeRule.onNodeWithText("Add to Library").performClick()
            waitForText("Saved")
            composeRule.onNodeWithText("Home").performClick()

            waitForTag(HomeTestTags.ROOT)
            composeRule.onNodeWithText(STORY_TITLE).assertIsDisplayed().performClick()
            waitForText("Saved")
            composeRule.onNodeWithText("Saved").performClick()
            waitForText("Add to Library")
            composeRule.onNodeWithContentDescription("Back").performClick()

            waitForTag(HomeTestTags.ROOT)
            waitForTextAbsent(STORY_TITLE)
            composeRule.onNodeWithText(STORY_TITLE).assertDoesNotExist()
        }
    }

    private fun markInitialSetupCompleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            check(createAppLaunchStateStore(context).markInitialSetupCompleted())
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(
            conditionDescription = "$tag is displayed",
            timeoutMillis = TIMEOUT_MS,
        ) {
            composeRule.onNodeWithTag(tag).isDisplayed()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(
            conditionDescription = "$text is displayed",
            timeoutMillis = TIMEOUT_MS,
        ) {
            composeRule.onAllNodesWithText(text)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun waitForTextAbsent(text: String) {
        composeRule.waitUntil(
            conditionDescription = "$text is absent",
            timeoutMillis = TIMEOUT_MS,
        ) {
            composeRule.onAllNodesWithText(text)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        }
    }

    private companion object {
        const val STORY_TITLE = "Kintsugi Days"
        const val TIMEOUT_MS = 5_000L
    }
}
