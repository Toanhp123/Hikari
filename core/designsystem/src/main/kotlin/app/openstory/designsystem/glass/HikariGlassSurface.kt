package app.openstory.designsystem.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

@Composable
internal fun hikariGlassSurfaceColor() =
    MaterialTheme.colorScheme.surface.copy(alpha = GLASS_SURFACE_ALPHA)

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
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = GLASS_BORDER_ALPHA)
    val mode = glassRenderingMode(Build.VERSION.SDK_INT)
    val glassModifier = if (mode == HikariGlassRenderingMode.BLUR && backdropScope != null) {
        modifier.drawBackdrop(
            backdrop = backdropScope.token.backdrop,
            shape = { shape },
            effects = { blur(8.dp.toPx()) },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(color = surfaceColor) },
        )
    } else {
        modifier
            .shadow(8.dp, shape, clip = false)
            .background(surfaceColor, shape)
    }
    Box(
        modifier = glassModifier
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

private const val GLASS_SURFACE_ALPHA = 0xD9 / 255f
private const val GLASS_BORDER_ALPHA = 0.72f
