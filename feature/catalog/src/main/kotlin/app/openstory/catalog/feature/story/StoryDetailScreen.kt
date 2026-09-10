package app.openstory.catalog.feature.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun StoryDetailScreen(
    state: StoryDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "story-header") { StoryHeader(state, onBack) }
        state.summary?.let { summary ->
            item(key = "story-summary") { StorySummary(summary) }
        }
        state.issue?.let { issue ->
            item(key = "story-issue") { StoryIssue(issue.retryable, onRetry) }
        }
        if (state.detailLoading && state.detail == null) {
            item(key = "story-detail-loading") {
                Text(
                    text = "Loading metadata...",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.detail?.let { detail ->
            item(key = "story-detail") {
                StoryMetadata(detail)
            }
        }
    }
}

@Composable
private fun StoryHeader(state: StoryDetailUiState, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 12.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Story detail", style = MaterialTheme.typography.labelLarge)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.summary?.title?.firstOrNull()?.uppercase() ?: "H",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun StorySummary(summary: StorySummaryUi) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(summary.title, style = MaterialTheme.typography.headlineLarge)
        summary.publicationStatus?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        summary.ratingLabel?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun StoryIssue(retryable: Boolean, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Story metadata could not be refreshed.")
            if (retryable) Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
private fun StoryMetadata(detail: StoryDetailUi) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        detail.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        MetadataLine("Authors", detail.authors)
        MetadataLine("Artists", detail.artists)
        MetadataLine("Genres", detail.genres)
        detail.publicationStatus?.let { MetadataLine("Status", listOf(it)) }
        detail.language?.let { MetadataLine("Language", listOf(it)) }
    }
}

@Composable
private fun MetadataLine(label: String, values: List<String>) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text(values.joinToString(), style = MaterialTheme.typography.bodyLarge)
    }
}
