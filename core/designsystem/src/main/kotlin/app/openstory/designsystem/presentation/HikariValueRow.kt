package app.openstory.designsystem.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.openstory.designsystem.theme.HikariDimensions

@Composable
fun HikariValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        leadingContent = leadingContent,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HikariDimensions.MinimumTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}
