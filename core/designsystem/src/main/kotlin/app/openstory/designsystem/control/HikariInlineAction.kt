package app.openstory.designsystem.control

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariShapes

@Composable
fun HikariInlineAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: HikariInlineActionTone = HikariInlineActionTone.DEFAULT,
    content: @Composable RowScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = when (tone) {
        HikariInlineActionTone.DEFAULT -> colorScheme.primary
        HikariInlineActionTone.DESTRUCTIVE -> colorScheme.error
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
        shape = MaterialTheme.hikariShapes.pill,
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        content = content,
    )
}
