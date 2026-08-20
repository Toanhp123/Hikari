package app.openstory.designsystem.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariInlineFeedback(
    message: String,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
    supportingText: String? = null,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.hikariSpacing.space16,
                vertical = MaterialTheme.hikariSpacing.space8,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (supportingText == null) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = supportingText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = actionModifier.heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
            ) { Text(actionLabel) }
        }
    }
}
