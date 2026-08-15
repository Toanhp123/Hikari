package app.openstory.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.designsystem.control.HikariIconAction
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.glass.HikariBackdropScope
import app.openstory.designsystem.icon.HikariBackGlyph
import app.openstory.designsystem.glass.HikariGlassPanel
import app.openstory.designsystem.glass.HikariGlassPanelStyle
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

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
            .padding(
                horizontal = MaterialTheme.hikariSpacing.space16,
                vertical = MaterialTheme.hikariSpacing.space12,
            ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HikariIconAction(
            onClick = onBack,
            contentDescription = "Back",
            backdropScope = backdropScope,
        ) { HikariBackGlyph() }
        HikariGlassPanel(
            backdropScope = backdropScope,
            modifier = Modifier.weight(1f),
            style = HikariGlassPanelStyle.TOOLBAR,
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
        HikariIconAction(
            onClick = onSettings,
            contentDescription = "Open reader settings",
            backdropScope = backdropScope,
        ) { Text("Aa", style = MaterialTheme.typography.titleMedium) }
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
    HikariGlassPanel(
        backdropScope = backdropScope,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = MaterialTheme.hikariSpacing.space16,
                vertical = MaterialTheme.hikariSpacing.space12,
            ),
        style = HikariGlassPanelStyle.FLOATING,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HikariUtilityAction(
                    onClick = { state.previousChapterId?.let(actions.onPreviousChapter) },
                    enabled = state.previousChapterId != null,
                    modifier = Modifier
                        .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
                        .testTag("reader-previous"),
                ) { Text("Previous") }
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                )
                HikariUtilityAction(
                    onClick = { state.nextChapterId?.let(actions.onNextChapter) },
                    enabled = state.nextChapterId != null,
                    modifier = Modifier
                        .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
                        .testTag("reader-next"),
                ) { Text("Next") }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun selectedSourceLabel(state: ReaderUiState): String = state.releases
    .firstOrNull { it.id == state.selectedReleaseId }
    ?.let { "${it.source} - ${it.languageTag}" }
    ?: "Reading view"
