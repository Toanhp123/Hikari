package app.openstory.designsystem.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing

/**
 * Compact rounded-rectangle action chrome for dense control bars.
 * Geometry stays centralized here so features do not fork button shapes.
 */
@Composable
fun HikariCompactAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val dimensions = MaterialTheme.hikariDimensions
    val shape = MaterialTheme.hikariShapes.compactControl
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = dimensions.minimumTouchTarget)
            .then(
                contentDescription?.let { description ->
                    Modifier.semantics { this.contentDescription = description }
                } ?: Modifier,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(dimensions.borderThin, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = dimensions.zero,
        shadowElevation = dimensions.zero,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space12),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun HikariCompactIconAction(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dimensions = MaterialTheme.hikariDimensions
    val shape = MaterialTheme.hikariShapes.compactControl
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(dimensions.minimumTouchTarget)
            .semantics { this.contentDescription = contentDescription },
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(dimensions.borderThin, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = dimensions.zero,
        shadowElevation = dimensions.zero,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
