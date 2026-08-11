package app.openstory.reader.ui

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun DownloadIndicator(availableOffline: Boolean) {
    if (availableOffline) {
        AssistChip(
            onClick = {},
            label = { Text("Offline") },
            modifier = Modifier.semantics { contentDescription = "Chapter available offline" },
        )
    }
}
