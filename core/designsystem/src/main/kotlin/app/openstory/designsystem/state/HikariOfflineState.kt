package app.openstory.designsystem.state

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HikariOfflineState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    HikariStateContent(
        title = title,
        modifier = modifier,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        titleColor = MaterialTheme.colorScheme.onSurface,
    )
}
