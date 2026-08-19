package app.openstory.designsystem.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HikariMetadataLine(
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    val metadata = items.filter(String::isNotBlank)
    if (metadata.isEmpty()) return
    Text(
        text = metadata.joinToString(" · "),
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
