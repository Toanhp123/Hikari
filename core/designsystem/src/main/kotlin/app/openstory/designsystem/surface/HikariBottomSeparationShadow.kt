package app.openstory.designsystem.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.theme.hikariColors
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariOpacity

/** Draws a subtle downward-only fade used to separate scrolled sticky chrome from content. */
@Composable
fun HikariBottomSeparationShadow(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (
        !enabled ||
        LocalHikariSurfaceShadowMode.current == HikariSurfaceShadowMode.DISABLED_FOR_BENCHMARK
    ) {
        return
    }
    val shadowRadius = MaterialTheme.hikariDimensions.surfaceShadowRadius
    val shadowColor = MaterialTheme.hikariColors.surfaceShadow.copy(
        alpha = MaterialTheme.hikariOpacity.surfaceShadow,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(shadowRadius + shadowRadius)
            .background(
                Brush.verticalGradient(
                    listOf(
                        shadowColor,
                        MaterialTheme.hikariColors.transparent,
                    ),
                ),
            )
            .testTag("hikari-bottom-separation-shadow"),
    )
}
