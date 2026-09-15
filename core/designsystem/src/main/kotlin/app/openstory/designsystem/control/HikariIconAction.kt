package app.openstory.designsystem.control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.designsystem.theme.HikariDimensions

@Composable
fun HikariIconAction(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: HikariIconActionStyle = HikariIconActionStyle.PLAIN,
    content: @Composable BoxScope.() -> Unit,
) {
    val containerColor = when (style) {
        HikariIconActionStyle.PLAIN -> Color.Transparent
        HikariIconActionStyle.TONAL -> MaterialTheme.colorScheme.surfaceVariant
        HikariIconActionStyle.OVERLAY -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }
    Box(
        modifier = Modifier
            .sizeIn(
                minWidth = HikariDimensions.MinimumTouchTarget,
                minHeight = HikariDimensions.MinimumTouchTarget,
            )
            .then(modifier)
            .clip(CircleShape)
            .background(containerColor)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
