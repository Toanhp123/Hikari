package app.openstory.settings.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.openstory.common.id.PluginId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.settings.background.SettingsBackgroundWorkStatus
import app.openstory.settings.notification.SettingsNotificationStatus
import app.openstory.settings.session.SettingsPluginSessionStatus
import app.openstory.settings.session.SettingsPluginSessionSummary
import app.openstory.settings.storage.SettingsStorageSummary
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactDark() = capture(settingsFixture(permissionGranted = false), true, "compact-dark.png")

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h960dp")
    fun mediumLight() = capture(settingsFixture(), false, "medium-light.png")

    private fun capture(state: SettingsUiState, dark: Boolean, fileName: String) {
        compose.setContent {
            HikariTheme(darkTheme = dark, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                SettingsScreen(state, {}, {}, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/settings/$fileName")
    }
}

internal fun settingsFixture(permissionGranted: Boolean = true) = SettingsUiState(
    pluginSessions = listOf(
        SettingsPluginSessionSummary(
            PluginId("org.openstory.content.mangadex"),
            "MangaDex",
            SettingsPluginSessionStatus.AUTHENTICATED,
            2_000,
        ),
    ),
    notificationStatus = SettingsNotificationStatus(permissionGranted, true, 2),
    backgroundWorkStatus = SettingsBackgroundWorkStatus(true, 1_000, null),
    storageSummary = SettingsStorageSummary(768L shl 20, 128L shl 20, 512L shl 20),
)
