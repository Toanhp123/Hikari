package app.openstory.designsystem.surface

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import app.openstory.designsystem.theme.hikariColors
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariOpacity

val LocalHikariSurfaceShadowMode = staticCompositionLocalOf { HikariSurfaceShadowMode.ENABLED }

/** Applies Hikari's centered, non-directional surface shadow. */
@Composable
fun Modifier.hikariSurfaceShadow(
    shape: Shape,
    enabled: Boolean = true,
): Modifier {
    if (
        !enabled ||
        LocalHikariSurfaceShadowMode.current == HikariSurfaceShadowMode.DISABLED_FOR_BENCHMARK
    ) {
        return this
    }
    return dropShadow(
        shape = shape,
        shadow = Shadow(
            radius = MaterialTheme.hikariDimensions.surfaceShadowRadius,
            color = MaterialTheme.hikariColors.surfaceShadow,
            offset = DpOffset.Zero,
            alpha = MaterialTheme.hikariOpacity.surfaceShadow,
        ),
    )
}
