package app.openstory.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import app.openstory.designsystem.theme.HikariTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class SettingsScreenSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun realStatusSectionsAndPermissionActionAreAccessible() {
        var permissionRequests = 0
        compose.setContent {
            HikariTheme {
                SettingsScreen(
                    state = settingsFixture(permissionGranted = false),
                    onBack = {},
                    onLogin = {},
                    onLogout = {},
                    onRequestNotificationPermission = { permissionRequests += 1 },
                )
            }
        }

        compose.onNodeWithContentDescription("Notification status").assertIsDisplayed()
        compose.onNodeWithContentDescription("Background work status").assertIsDisplayed()
        compose.onNodeWithContentDescription("Storage summary").assertIsDisplayed()
        compose.onNodeWithText("Allow notifications").performClick()
        assertEquals(1, permissionRequests)
    }

    @Test
    fun stableErrorsNeverRenderSecretBearingExceptionText() {
        compose.setContent {
            HikariTheme {
                SettingsScreen(
                    state = settingsFixture().copy(authenticationErrorCode = "settings.auth_login_failed"),
                    onBack = {},
                    onLogin = {},
                    onLogout = {},
                    onRequestNotificationPermission = {},
                )
            }
        }
        compose.onNodeWithTag("settings-list")
            .performScrollToKey("error-settings.auth_login_failed")
        compose.onNodeWithText("Login could not be started.").assertIsDisplayed()
        compose.onNodeWithText("secret", substring = true).assertDoesNotExist()
    }
}
