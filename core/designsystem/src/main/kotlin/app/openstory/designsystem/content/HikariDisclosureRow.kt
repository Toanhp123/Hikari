package app.openstory.designsystem.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.designsystem.icon.HikariDisclosureGlyph
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariDisclosureRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val dimensions = MaterialTheme.hikariDimensions
    Surface(
        onClick = onToggle,
        modifier = modifier.semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        },
        shape = MaterialTheme.hikariShapes.contentCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = dimensions.zero,
        shadowElevation = dimensions.zero,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensions.minimumTouchTarget)
                .padding(
                    horizontal = MaterialTheme.hikariSpacing.space12,
                    vertical = MaterialTheme.hikariSpacing.space8,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            trailing?.invoke()
            HikariDisclosureGlyph(
                expanded = expanded,
                modifier = Modifier.size(dimensions.iconMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
