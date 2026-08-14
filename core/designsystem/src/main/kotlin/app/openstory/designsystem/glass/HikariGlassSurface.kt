package app.openstory.designsystem.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariOpacity
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

@Composable
internal fun hikariGlassSurfaceColor() =
    MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.hikariOpacity.glassSurface)

@Composable
internal fun hikariGlassContentColor() = MaterialTheme.colorScheme.onSurface

@Composable
fun HikariGlassSurface(
    backdropScope: HikariBackdropScope?,
    modifier: Modifier = Modifier,
    shape: Shape,
    contentPadding: PaddingValues = PaddingValues.Zero,
    content: @Composable () -> Unit,
) {
    val surfaceColor = hikariGlassSurfaceColor()
    val contentColor = hikariGlassContentColor()
    val dimensions = MaterialTheme.hikariDimensions
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaterialTheme.hikariOpacity.glassBorder)
    val mode = glassRenderingMode(Build.VERSION.SDK_INT)
    val glassModifier = if (mode == HikariGlassRenderingMode.BLUR && backdropScope != null) {
        modifier.drawBackdrop(
            backdrop = backdropScope.token.backdrop,
            shape = { shape },
            effects = { blur(dimensions.glassBlurRadius.toPx()) },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(color = surfaceColor) },
        )
    } else {
        modifier
            .shadow(dimensions.glassShadowElevation, shape, clip = false)
            .background(surfaceColor, shape)
    }
    Box(
        modifier = glassModifier
            .border(dimensions.borderThin, borderColor, shape)
            .clip(shape)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
