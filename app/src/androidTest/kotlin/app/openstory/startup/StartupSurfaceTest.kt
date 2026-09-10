package app.openstory.startup

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.openstory.startup.ui.FirstRunScreen
import app.openstory.startup.ui.UnknownScreen
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.ui.HikariBootSurface
import org.junit.Rule
import org.junit.Test

class StartupSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unknownSurfaceDoesNotPretendProductContentIsLoading() {
        setStartupContent {
            UnknownScreen()
        }

        composeRule.onNodeWithTag("startup-unknown").assertExists()
        composeRule.onNodeWithTag("startup-first-run").assertDoesNotExist()
        composeRule.onNodeWithTag("catalog-discover").assertDoesNotExist()
    }

    @Test
    fun firstRunFailureRemainsRetryable() {
        setStartupContent {
            FirstRunScreen(
                isSaving = false,
                saveFailed = true,
                onComplete = {},
            )
        }

        composeRule.onNodeWithTag("startup-completion-error").assertExists()
        composeRule.onNodeWithTag("startup-complete").assertIsEnabled()
    }

    private fun setStartupContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariBootSurface(content = content)
            }
        }
    }
}
