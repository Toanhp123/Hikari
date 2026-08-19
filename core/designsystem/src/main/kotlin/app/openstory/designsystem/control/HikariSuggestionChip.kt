package app.openstory.designsystem.control

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariShapes

@Composable
fun HikariSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AssistChip(
        onClick = onClick,
        label = label,
        enabled = enabled,
        modifier = modifier.sizeIn(
            minWidth = MaterialTheme.hikariDimensions.minimumTouchTarget,
            minHeight = MaterialTheme.hikariDimensions.minimumTouchTarget,
        ),
        shape = MaterialTheme.hikariShapes.pill,
    )
}
