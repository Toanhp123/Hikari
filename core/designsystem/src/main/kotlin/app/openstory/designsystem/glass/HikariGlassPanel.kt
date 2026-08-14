package app.openstory.designsystem.glass

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariGlassPanel(
    backdropScope: HikariBackdropScope?,
    modifier: Modifier = Modifier,
    style: HikariGlassPanelStyle = HikariGlassPanelStyle.STANDARD,
    content: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.hikariSpacing
    val shapes = MaterialTheme.hikariShapes
    val shape: Shape
    val padding: PaddingValues
    when (style) {
        HikariGlassPanelStyle.COMPACT -> {
            shape = shapes.compactCard
            padding = PaddingValues(spacing.space12)
        }
        HikariGlassPanelStyle.STANDARD -> {
            shape = shapes.contentCard
            padding = PaddingValues(spacing.space14)
        }
        HikariGlassPanelStyle.PROMINENT -> {
            shape = shapes.prominentCard
            padding = PaddingValues(spacing.space14)
        }
        HikariGlassPanelStyle.TOOLBAR -> {
            shape = shapes.sheetCard
            padding = PaddingValues(horizontal = spacing.space18, vertical = spacing.space12)
        }
        HikariGlassPanelStyle.FLOATING -> {
            shape = shapes.hero
            padding = PaddingValues(horizontal = spacing.space12, vertical = spacing.space8)
        }
    }
    HikariGlassSurface(
        backdropScope = backdropScope,
        modifier = modifier,
        shape = shape,
        contentPadding = padding,
        content = content,
    )
}
