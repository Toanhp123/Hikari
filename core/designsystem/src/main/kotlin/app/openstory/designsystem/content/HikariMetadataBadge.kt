package app.openstory.designsystem.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.openstory.designsystem.theme.hikariOpacity
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariMetadataBadge(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(
        alpha = MaterialTheme.hikariOpacity.metadataBadge,
    ),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = label,
        modifier = modifier
            .background(containerColor, MaterialTheme.hikariShapes.pill)
            .padding(
                horizontal = MaterialTheme.hikariSpacing.space10,
                vertical = MaterialTheme.hikariSpacing.space5,
            ),
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
    )
}
