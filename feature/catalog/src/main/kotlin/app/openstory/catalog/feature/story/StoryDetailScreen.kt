package app.openstory.catalog.feature.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun StoryDetailScreen(
    state: StoryDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        StoryDetailContent(
            state = state,
            layout = storyLayout(maxWidth >= StoryVisualMetrics.WideLayoutThreshold),
            onBack = onBack,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun StoryDetailContent(
    state: StoryDetailUiState,
    layout: StoryLayoutMetrics,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = layout.screenInset,
            end = layout.screenInset,
            bottom = MaterialTheme.hikariSpacing.space32,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space24),
    ) {
        item(key = "story-back") { StoryBack(onBack) }
        item(key = "story-hero") {
            StoryHero(
                state = state,
                coverWidth = layout.coverWidth,
                coverHeight = layout.coverHeight,
                identityGap = layout.identityGap,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.issue?.let { issue -> item(key = "story-issue") { StoryIssue(issue.retryable, onRetry) } }
        if (state.detailLoading && state.detail == null) {
            item(key = "story-detail-loading") { StoryMetadataSkeleton() }
        }
        state.detail?.let { detail -> item(key = "story-detail") {
            StoryMetadataSections(detail = detail, modifier = Modifier.fillMaxWidth())
        } }
    }
}

@Composable
private fun StoryBack(onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.height(48.dp)) { Text("Back") }
}

@Composable
private fun StoryIssue(retryable: Boolean, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        HikariInlineFeedback(
            message = "Story metadata could not be refreshed.",
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
            actionLabel = "Try again".takeIf { retryable },
            onAction = onRetry.takeIf { retryable },
        )
    }
}

@Composable
private fun StoryMetadataSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("story-detail-skeleton"),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        HikariSkeleton(
            modifier = Modifier.fillMaxWidth(METADATA_HEADING_SKELETON_WIDTH_FRACTION).height(26.dp),
            shape = MaterialTheme.shapes.small,
        )
        HikariSkeleton(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = MaterialTheme.shapes.medium,
        )
    }
}

@Composable
private fun storyLayout(wide: Boolean): StoryLayoutMetrics = if (wide) {
    StoryLayoutMetrics(
        screenInset = MaterialTheme.hikariSpacing.space32,
        coverWidth = StoryVisualMetrics.WideCoverWidth,
        coverHeight = StoryVisualMetrics.WideCoverHeight,
        identityGap = MaterialTheme.hikariSpacing.space24,
    )
} else {
    StoryLayoutMetrics(
        screenInset = MaterialTheme.hikariSpacing.space20,
        coverWidth = StoryVisualMetrics.CompactCoverWidth,
        coverHeight = StoryVisualMetrics.CompactCoverHeight,
        identityGap = MaterialTheme.hikariSpacing.space16,
    )
}

private data class StoryLayoutMetrics(
    val screenInset: Dp,
    val coverWidth: Dp,
    val coverHeight: Dp,
    val identityGap: Dp,
)

private const val METADATA_HEADING_SKELETON_WIDTH_FRACTION = 0.3f
