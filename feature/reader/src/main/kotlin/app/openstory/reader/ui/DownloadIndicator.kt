package app.openstory.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.designsystem.content.HikariMetadataBadge

@Composable
fun DownloadIndicator(availableOffline: Boolean) {
    if (availableOffline) {
        HikariMetadataBadge(
            label = "Offline",
            modifier = Modifier.semantics { contentDescription = "Chapter available offline" },
        )
    }
}
