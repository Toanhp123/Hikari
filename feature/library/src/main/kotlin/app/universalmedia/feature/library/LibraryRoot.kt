package app.universalmedia.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.RootId

@Composable
fun LibraryRoot(
    state: LibraryUiState,
    onAddRoot: () -> Unit,
    onMediaSelected: (MediaId) -> Unit,
    onRetryScan: (RootId) -> Unit,
    onReloadLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = "Universal Media", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onAddRoot, enabled = !state.isAddingRoot) {
                Text(if (state.isAddingRoot) "Adding folder..." else "Add Folder")
            }
        }
        state.error?.let { error ->
            item {
                Text(
                    when (error) {
                        LibraryError.REGISTRATION ->
                            "Folder access failed. Choose the folder again."

                        LibraryError.STORAGE ->
                            "Could not save the folder. Try adding it again."

                        LibraryError.SCHEDULING -> "Could not schedule scan. Try again."

                        LibraryError.LIBRARY -> "Could not load Library. Try again."
                    },
                    color = MaterialTheme.colorScheme.error,
                )
                if (error == LibraryError.LIBRARY) {
                    TextButton(onClick = onReloadLibrary) { Text("Reload Library") }
                }
            }
        }
        items(state.roots, key = { "root:${it.rootId.value}" }) { root ->
            Text("Folder: ${root.status.label()}")
            if (root.status == LibraryScanStatus.ACCESS_LOST) {
                TextButton(onClick = onAddRoot, enabled = !state.isAddingRoot) {
                    Text("Authorize Folder")
                }
            } else {
                TextButton(onClick = { onRetryScan(root.rootId) }) { Text("Scan Folder") }
            }
        }
        if (state.cards.isEmpty()) item { Text("No videos in Library yet.") }
        items(state.cards, key = { "media:${it.mediaId.value}" }) { card ->
            TextButton(
                onClick = { onMediaSelected(card.mediaId) },
                modifier = Modifier.testTag("media:${card.mediaId.value}"),
            ) { Text(card.label) }
        }
    }
}

private fun LibraryScanStatus.label(): String = when (this) {
    LibraryScanStatus.IDLE -> "Ready to scan"
    LibraryScanStatus.RUNNING -> "Scan started; waiting for completion"
    LibraryScanStatus.COMPLETE -> "Scan complete"
    LibraryScanStatus.PARTIAL -> "Scan incomplete; retry available"
    LibraryScanStatus.FAILED -> "Scan failed; retry available"
    LibraryScanStatus.CANCELLED -> "Scan cancelled; retry available"
    LibraryScanStatus.INTERRUPTED -> "Scan interrupted; retry available"
    LibraryScanStatus.ACCESS_LOST -> "Folder access lost"
    LibraryScanStatus.UNAVAILABLE -> "Folder unavailable; retry available"
}
