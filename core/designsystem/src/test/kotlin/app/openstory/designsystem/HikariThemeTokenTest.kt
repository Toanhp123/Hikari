package app.openstory.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HikariThemeTokenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lightThemeExposesHikariProductTokens() {
        var primary = Color.Unspecified
        var secondary = Color.Unspecified
        var background = Color.Unspecified
        var headlineFamily: FontFamily? = null
        var mediumShape: Shape? = null

        compose.setContent {
            HikariTheme(darkTheme = false) {
                val colors = MaterialTheme.colorScheme
                val typography = MaterialTheme.typography
                val shapes = MaterialTheme.shapes
                SideEffect {
                    primary = colors.primary
                    secondary = colors.secondary
                    background = colors.background
                    headlineFamily = typography.headlineLarge.fontFamily
                    mediumShape = shapes.medium
                }
            }
        }

        compose.runOnIdle {
            assertEquals(Color(0xFFFF7461), primary)
            assertEquals(Color(0xFF2E8B80), secondary)
            assertEquals(Color(0xFFF6F0E8), background)
            assertEquals(FontFamily.Serif, headlineFamily)
            assertEquals(RoundedCornerShape(20.dp), mediumShape)
        }
    }
}
