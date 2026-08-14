package app.openstory.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openstory.designsystem.theme.HikariDarkColorScheme
import app.openstory.designsystem.theme.HikariLightColorScheme
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.designsystem.theme.hikariBreakpoints
import app.openstory.designsystem.theme.hikariColors
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariLayoutPolicy
import app.openstory.designsystem.theme.hikariTypography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HikariThemeTokensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeOwnsMaterialSurfaceContainerRoles() {
        assertEquals(Color(0xFFFFF9F2), HikariLightColorScheme.surfaceBright)
        assertEquals(Color(0xFFDED4CB), HikariLightColorScheme.surfaceDim)
        assertEquals(Color(0xFFFFF9F2), HikariLightColorScheme.surfaceContainerLowest)
        assertEquals(Color(0xFFF6F0E8), HikariLightColorScheme.surfaceContainerLow)
        assertEquals(Color(0xFFF2EAE1), HikariLightColorScheme.surfaceContainer)
        assertEquals(Color(0xFFEFE6DD), HikariLightColorScheme.surfaceContainerHigh)
        assertEquals(Color(0xFFECE3DA), HikariLightColorScheme.surfaceContainerHighest)

        assertEquals(Color(0xFF24302A), HikariDarkColorScheme.surfaceBright)
        assertEquals(Color(0xFF101714), HikariDarkColorScheme.surfaceDim)
        assertEquals(Color(0xFF101714), HikariDarkColorScheme.surfaceContainerLowest)
        assertEquals(Color(0xFF141C18), HikariDarkColorScheme.surfaceContainerLow)
        assertEquals(Color(0xFF18211D), HikariDarkColorScheme.surfaceContainer)
        assertEquals(Color(0xFF1E2924), HikariDarkColorScheme.surfaceContainerHigh)
        assertEquals(Color(0xFF24302A), HikariDarkColorScheme.surfaceContainerHighest)
    }

    @Test
    fun themeExposesApprovedProductTokens() {
        var minimumTouchTarget: Dp = Dp.Unspecified
        var expandedContent: Dp = Dp.Unspecified
        var compactGridColumns = -1
        var readerBodySize: TextUnit = TextUnit.Unspecified
        var onArtwork: Color = Color.Unspecified

        compose.setContent {
            HikariTheme(darkTheme = true) {
                minimumTouchTarget = MaterialTheme.hikariDimensions.minimumTouchTarget
                expandedContent = MaterialTheme.hikariBreakpoints.expandedContent
                compactGridColumns = MaterialTheme.hikariLayoutPolicy.compactGridColumns
                readerBodySize = MaterialTheme.hikariTypography.readerBody.fontSize
                onArtwork = MaterialTheme.hikariColors.onArtwork
                SideEffect { }
            }
        }

        compose.runOnIdle {
            assertEquals(48.dp, minimumTouchTarget)
            assertEquals(520.dp, expandedContent)
            assertEquals(2, compactGridColumns)
            assertEquals(18.sp, readerBodySize)
            assertEquals(Color.White, onArtwork)
        }
    }
}
