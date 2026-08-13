package app.openstory.designsystem.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HikariMetadataBadge(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = BADGE_ALPHA),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = label,
        modifier = modifier
            .background(containerColor, RoundedCornerShape(percent = BADGE_CORNER_PERCENT))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
    )
}

private const val BADGE_ALPHA = 0.72f
private const val BADGE_CORNER_PERCENT = 50
