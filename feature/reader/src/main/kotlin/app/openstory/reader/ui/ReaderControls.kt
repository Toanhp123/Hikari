package app.openstory.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReaderControls(state: ReaderUiState, actions: ReaderActions) {
    TopAppBar(
        title = { Text(state.chapterLabel.ifBlank { "Reader" }) },
        actions = {
            DownloadIndicator(state.availableOffline)
            TextButton(
                onClick = actions.onDecreaseFont,
                enabled = state.fontScale > MIN_FONT_SCALE,
                modifier = Modifier.semantics { contentDescription = "Decrease reader text size" },
            ) { Text("A−") }
            TextButton(
                onClick = actions.onIncreaseFont,
                enabled = state.fontScale < MAX_FONT_SCALE,
                modifier = Modifier.semantics { contentDescription = "Increase reader text size" },
            ) { Text("A+") }
            ReleaseSwitcher(state.releases, state.selectedReleaseId, actions.onReleaseSelected)
        },
    )
}

@Composable
fun ReaderChapterNavigation(state: ReaderUiState, actions: ReaderActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = { state.previousChapterId?.let(actions.onPreviousChapter) },
            enabled = state.previousChapterId != null,
        ) { Text("Previous") }
        TextButton(
            onClick = { state.nextChapterId?.let(actions.onNextChapter) },
            enabled = state.nextChapterId != null,
        ) { Text("Next") }
    }
}
