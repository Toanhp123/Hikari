package app.openstory.designsystem.menu

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.surface.hikariSurfaceShadow
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariShapes

@Composable
fun HikariDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.hikariShapes.contentCard
    val zero = MaterialTheme.hikariDimensions.zero
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.hikariSurfaceShadow(shape),
        shape = shape,
        tonalElevation = zero,
        shadowElevation = zero,
        content = content,
    )
}
