package app.openstory.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.ui.HikariAppShell
import app.openstory.ui.HikariUtilitySheet
import app.openstory.ui.hikariTopLevelContentPadding
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppShellScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeSelectedDark() = captureShell(AppRoute.Home, darkTheme = true, "home-dark.png")

    @Test
    fun discoverSelectedLight() = captureShell(
        AppRoute.Discover,
        darkTheme = false,
        "discover-light.png",
    )

    @Test
    fun downloadsAndUpdatesUtilitySheet() {
        compose.setContent {
            HikariTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().background(Color(0xFF101417))) {
                    HikariUtilitySheet(onDismiss = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/app-shell/utility-sheet.png")
    }

    private fun captureShell(route: AppRoute, darkTheme: Boolean, fileName: String) {
        compose.setContent {
            HikariTheme(darkTheme = darkTheme) {
                HikariAppShell(
                    currentRoute = route,
                    onTopLevelSelected = {},
                    onUtilityRequested = {},
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF315F74))
                            .hikariTopLevelContentPadding(),
                    ) {
                        Text(route.toString())
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/app-shell/$fileName")
    }
}
