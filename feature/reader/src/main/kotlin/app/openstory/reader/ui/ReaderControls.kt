package app.openstory.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.glass.HikariBackdropScope
import app.openstory.designsystem.glass.HikariGlassSurface

@Composable
fun ReaderControls(
    state: ReaderUiState,
    backdropScope: HikariBackdropScope,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderRoundAction("<", "Back", backdropScope, onBack)
        HikariGlassSurface(
            backdropScope = backdropScope,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Column {
                Text(
                    text = state.chapterLabel.ifBlank { "Reader" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = selectedSourceLabel(state),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ReaderRoundAction("Aa", "Open reader settings", backdropScope, onSettings)
    }
}

@Composable
fun ReaderChapterNavigation(
    state: ReaderUiState,
    progress: Float,
    backdropScope: HikariBackdropScope,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
) {
    HikariGlassSurface(
        backdropScope = backdropScope,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { state.previousChapterId?.let(actions.onPreviousChapter) },
                    enabled = state.previousChapterId != null,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Previous") }
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(
                    onClick = { state.nextChapterId?.let(actions.onNextChapter) },
                    enabled = state.nextChapterId != null,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Next") }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReaderRoundAction(
    label: String,
    description: String,
    backdropScope: HikariBackdropScope,
    onClick: () -> Unit,
) {
    HikariGlassSurface(
        backdropScope = backdropScope,
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        shape = CircleShape,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun selectedSourceLabel(state: ReaderUiState): String = state.releases
    .firstOrNull { it.id == state.selectedReleaseId }
    ?.let { "${it.source} - ${it.languageTag}" }
    ?: "Reading view"
