package app.openstory.designsystem.surface

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariShapes

@Composable
fun HikariContentCard(
    modifier: Modifier = Modifier,
    style: HikariContentCardStyle = HikariContentCardStyle.STANDARD,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = hikariContentCardShape(style)
    val dimensions = MaterialTheme.hikariDimensions
    val colorScheme = MaterialTheme.colorScheme
    val cardModifier = modifier.hikariSurfaceShadow(
        shape = shape,
        enabled = onClick == null || enabled,
    )
    if (onClick == null) {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = colorScheme.surface,
            contentColor = colorScheme.onSurface,
            tonalElevation = dimensions.zero,
            shadowElevation = dimensions.zero,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = cardModifier,
            enabled = enabled,
            shape = shape,
            color = if (enabled) colorScheme.surface else colorScheme.surfaceContainerLow,
            contentColor = if (enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            tonalElevation = dimensions.zero,
            shadowElevation = dimensions.zero,
            content = content,
        )
    }
}

@Composable
private fun hikariContentCardShape(style: HikariContentCardStyle): Shape = when (style) {
    HikariContentCardStyle.STANDARD -> MaterialTheme.hikariShapes.contentCard
    HikariContentCardStyle.PROMINENT -> MaterialTheme.hikariShapes.sheetCard
    HikariContentCardStyle.SHEET -> MaterialTheme.hikariShapes.sheetCard
}
