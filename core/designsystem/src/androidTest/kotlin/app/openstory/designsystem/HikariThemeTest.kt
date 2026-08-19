package app.openstory.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.theme.HikariSpacing
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.designsystem.theme.hikariSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class HikariThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun exposesFoundationSpacingTokens() {
        var spacing: HikariSpacing? = null

        compose.setContent {
            HikariTheme {
                val currentSpacing = MaterialTheme.hikariSpacing
                SideEffect { spacing = currentSpacing }
            }
        }

        compose.runOnIdle {
            assertEquals(16.dp, spacing?.space16)
            assertEquals(24.dp, spacing?.space24)
        }
    }

    @Test
    fun lightAndDarkThemesUseDifferentBackgrounds() {
        var darkTheme by mutableStateOf(false)
        var background = Color.Unspecified

        compose.setContent {
            HikariTheme(darkTheme = darkTheme) {
                val currentBackground = MaterialTheme.colorScheme.background
                SideEffect { background = currentBackground }
            }
        }

        var lightBackground: Color? = null
        compose.runOnIdle {
            lightBackground = background
            darkTheme = true
        }
        compose.runOnIdle {
            assertNotEquals(lightBackground, background)
        }
    }
}
