package app.openstory.designsystem.control

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariShapes

@Composable
fun HikariPrimaryAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
        shape = MaterialTheme.hikariShapes.pill,
        elevation = null,
        content = content,
    )
}
