package app.openstory.designsystem.feedback

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HikariConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    style: HikariConfirmationStyle = HikariConfirmationStyle.STANDARD,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = when (style) {
                        HikariConfirmationStyle.STANDARD -> MaterialTheme.colorScheme.primary
                        HikariConfirmationStyle.DESTRUCTIVE -> MaterialTheme.colorScheme.error
                    },
                ),
            ) {
                Text(text = confirmLabel)
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel)
            }
        },
        title = { Text(text = title) },
        text = { Text(text = message) },
    )
}
