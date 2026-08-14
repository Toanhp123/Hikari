package app.openstory.designsystem.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariOpacity
import app.openstory.designsystem.theme.hikariShapes

@Composable
fun HikariContentAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: HikariContentActionTone = HikariContentActionTone.DEFAULT,
    content: @Composable RowScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dimensions = MaterialTheme.hikariDimensions
    val contentColor = when (tone) {
        HikariContentActionTone.DEFAULT -> colorScheme.primary
        HikariContentActionTone.DESTRUCTIVE -> colorScheme.error
    }
    val borderColor = if (enabled) {
        contentColor.copy(alpha = MaterialTheme.hikariOpacity.accentBorder)
    } else {
        colorScheme.outlineVariant
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = dimensions.minimumTouchTarget),
        shape = MaterialTheme.hikariShapes.pill,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        border = BorderStroke(dimensions.borderThin, borderColor),
        content = content,
    )
}
