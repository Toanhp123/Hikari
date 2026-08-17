package app.openstory.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HikariStateComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingExposesItsLabel() {
        compose.setContent {
            HikariTheme { HikariLoadingState(label = "Loading chapters") }
        }

        compose.onNodeWithText("Loading chapters").assertIsDisplayed()
    }

    @Test
    fun emptyActionInvokesCallback() {
        var invoked = false
        compose.setContent {
            HikariTheme {
                HikariEmptyState(
                    title = "Nothing saved",
                    actionLabel = "Browse",
                    onAction = { invoked = true },
                )
            }
        }

        compose.onNodeWithText("Browse").performClick()
        compose.runOnIdle { assertTrue(invoked) }
    }

    @Test
    fun errorRetryInvokesCallback() {
        var invoked = false
        compose.setContent {
            HikariTheme {
                HikariErrorState(
                    title = "Could not load",
                    actionLabel = "Retry",
                    onAction = { invoked = true },
                )
            }
        }

        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertTrue(invoked) }
    }
}
