package app.openstory.designsystem.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.HikariDimensions

@Composable
fun HikariInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        leadingContent = leadingContent,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HikariDimensions.MinimumTouchTarget)
            .then(modifier),
    )
}
