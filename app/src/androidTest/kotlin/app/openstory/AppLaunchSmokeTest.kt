package app.openstory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class AppLaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun freshInstallReachesFirstRunShell() {
        val firstRun = composeRule.onNodeWithTag("startup-first-run")
        composeRule.waitUntil(
            conditionDescription = "FirstRun shell is displayed",
            timeoutMillis = 5_000,
            condition = firstRun::isDisplayed,
        )
        firstRun.assertIsDisplayed()
    }
}
