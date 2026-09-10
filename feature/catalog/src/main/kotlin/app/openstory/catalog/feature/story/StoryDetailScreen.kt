package app.openstory.catalog.feature.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.openstory.catalog.feature.assets.CoverArtwork
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.state.HikariSkeleton

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
                HikariSkeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(72.dp)
                        .testTag("story-detail-skeleton"),
                    shape = MaterialTheme.shapes.medium,
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
        CoverArtwork(
            title = state.summary?.title ?: "Story cover",
            locator = state.coverLocator,
            assetKey = state.coverAssetKey,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )
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
        HikariInlineFeedback(
            message = "Story metadata could not be refreshed.",
            modifier = Modifier.padding(18.dp),
            actionLabel = "Try again".takeIf { retryable },
            onAction = onRetry.takeIf { retryable },
        )
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
