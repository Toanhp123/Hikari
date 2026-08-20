package app.openstory.designsystem.control

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.hikariDimensions

@Composable
fun HikariFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        enabled = enabled,
        modifier = modifier.sizeIn(
            minWidth = MaterialTheme.hikariDimensions.minimumTouchTarget,
            minHeight = MaterialTheme.hikariDimensions.minimumTouchTarget,
        ),
    )
}
