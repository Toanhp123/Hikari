package app.openstory.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.glass.HikariBackdropHost
import app.openstory.designsystem.glass.HikariGlassSurface
import app.openstory.designsystem.theme.HikariTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HikariProductPrimitivesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(sdk = [26])
    fun api26UsesTranslucentGlassWithStableGeometry() {
        captureGlass(
            darkTheme = true,
            fileName = "api26-dark-fallback.png",
        )
    }

    @Test
    @Config(sdk = [35])
    fun currentApiUsesBlurGlassWithStableGeometry() {
        captureGlass(
            darkTheme = false,
            fileName = "api35-light-blur.png",
        )
    }

    @Test
    @Config(sdk = [35])
    fun currentApiUsesDarkThemeGlassWithStableGeometry() {
        captureGlass(
            darkTheme = true,
            fileName = "api35-dark-blur.png",
        )
    }

    private fun captureGlass(
        darkTheme: Boolean,
        fileName: String,
    ) {
        compose.setContent {
            HikariTheme(darkTheme = darkTheme) {
                HikariBackdropHost(
                    modifier = Modifier.size(width = 360.dp, height = 220.dp),
                    background = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFB46854), Color(0xFF315F74)),
                                    ),
                                ),
                        )
                    },
                ) {
                    HikariGlassSurface(
                        backdropScope = this,
                        modifier = Modifier.padding(32.dp),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    ) {
                        Text("Hikari glass")
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage("src/test/snapshots/product-primitives/$fileName")
    }
}
