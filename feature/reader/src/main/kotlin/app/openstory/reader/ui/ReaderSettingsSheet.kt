package app.openstory.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReaderSettingsSheet(
    state: ReaderUiState,
    actions: ReaderActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = MaterialTheme.hikariSpacing.space20,
                vertical = MaterialTheme.hikariSpacing.space12,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space14),
        ) {
            Text("Reading settings", style = MaterialTheme.typography.headlineSmall)
            Text("Text size", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = actions.onDecreaseFont,
                    enabled = state.fontScale > MIN_FONT_SCALE,
                    modifier = Modifier
                        .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
                        .semantics { contentDescription = "Decrease reader text size" },
                ) { Text("A-") }
                Text(
                    "${(state.fontScale * PERCENT_MULTIPLIER).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(
                    onClick = actions.onIncreaseFont,
                    enabled = state.fontScale < MAX_FONT_SCALE,
                    modifier = Modifier
                        .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
                        .semantics { contentDescription = "Increase reader text size" },
                ) { Text("A+") }
            }
            Text("Reading source", style = MaterialTheme.typography.titleMedium)
            ReleaseSwitcher(state.releases, state.selectedReleaseId, actions.onReleaseSelected)
            Text("Download", style = MaterialTheme.typography.titleMedium)
            if (state.availableOffline) {
                DownloadIndicator(true)
            } else {
                Text(
                    "This chapter is not available offline.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private const val PERCENT_MULTIPLIER = 100
