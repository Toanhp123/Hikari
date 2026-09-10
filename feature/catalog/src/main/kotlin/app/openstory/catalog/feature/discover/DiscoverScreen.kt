package app.openstory.catalog.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.feature.state.CatalogIssueUi
import app.openstory.designsystem.control.HikariSegmentedControl
import app.openstory.designsystem.control.HikariSegmentedOption
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun DiscoverScreen(
    state: DiscoverUiState,
    listState: LazyListState,
    onMediaSelected: (CatalogMediaType) -> Unit,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    val durableContent = state.content is DiscoverContentState.Empty ||
        state.content is DiscoverContentState.Content
    val refreshing = when (val content = state.content) {
        is DiscoverContentState.Empty -> content.refreshing
        is DiscoverContentState.Content -> content.refreshing
        else -> false
    }
    HikariPullToRefresh(
        refreshing = refreshing,
        enabled = durableContent,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(DiscoverTestTags.ROOT),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space20),
        ) {
            item(key = "discover-header") {
                DiscoverHeader(
                    state = state,
                    onMediaSelected = onMediaSelected,
                )
            }

            when (val content = state.content) {
                DiscoverContentState.NoContentLoading -> loadingSections()
                is DiscoverContentState.NoContentFailure -> item(key = "discover-failure") {
                    DiscoverFailureState(content.issue, onRetry)
                }
                is DiscoverContentState.Empty -> {
                    content.issue?.let { issue -> item(key = "discover-empty-issue") {
                        DiscoverIssuePanel(issue, onRetry)
                    } }
                    item(key = "discover-empty") { DiscoverEmptyState() }
                }
                is DiscoverContentState.Content -> {
                    content.issue?.let { issue -> item(key = "discover-content-issue") {
                        DiscoverIssuePanel(issue, onRetry)
                    } }
                    discoverSections(content.sections, onStorySelected)
                }
            }

            item(key = "discover-bottom-space") {
                Spacer(modifier = Modifier.padding(bottom = MaterialTheme.hikariSpacing.space12))
            }
        }
    }
}

@Composable
private fun DiscoverHeader(
    state: DiscoverUiState,
    onMediaSelected: (CatalogMediaType) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        Text(
            text = "Discover",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Three distinct signals. One deliberately bounded shelf.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HikariSegmentedControl(
            options = DISCOVER_MEDIA_OPTIONS,
            selectedKey = state.selectedMediaType,
            onSelected = onMediaSelected,
        )
    }
}

@Composable
private fun DiscoverIssuePanel(issue: CatalogIssueUi, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        HikariInlineFeedback(
            message = issue.kind.message,
            modifier = Modifier.padding(18.dp),
            actionLabel = "Try again".takeIf { issue.retryable },
            onAction = onRetry.takeIf { issue.retryable },
        )
    }
}

@Composable
private fun DiscoverFailureState(issue: CatalogIssueUi, onRetry: () -> Unit) {
    HikariErrorState(
        title = issue.kind.message,
        modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space20),
        actionLabel = "Try again".takeIf { issue.retryable },
        onAction = onRetry.takeIf { issue.retryable },
    )
}

@Composable
private fun DiscoverEmptyState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag(DiscoverTestTags.EMPTY),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
    ) {
        HikariEmptyState(
            title = "Nothing published yet",
            body = "This source completed successfully, but has no eligible stories for this medium.",
            modifier = Modifier.padding(28.dp),
        )
    }
}

private val CatalogMediaType.label: String
    get() = when (this) {
        CatalogMediaType.MANGA -> "Manga"
        CatalogMediaType.LIGHT_NOVEL -> "Light Novel"
    }

private val DISCOVER_MEDIA_OPTIONS = listOf(
    HikariSegmentedOption(CatalogMediaType.MANGA, CatalogMediaType.MANGA.label),
    HikariSegmentedOption(CatalogMediaType.LIGHT_NOVEL, CatalogMediaType.LIGHT_NOVEL.label),
)

private val CatalogIssueKind.message: String
    get() = when (this) {
        CatalogIssueKind.SOURCE_UNAVAILABLE -> "No catalog source is available in this build."
        CatalogIssueKind.INVALID_SOURCE_DATA -> "The source returned data that could not be safely displayed."
        CatalogIssueKind.ACQUISITION_FAILED -> "The catalog could not be refreshed."
        CatalogIssueKind.STORAGE_FAILED -> "Saved catalog data could not be read."
        CatalogIssueKind.ARTWORK_FAILED -> "Some artwork could not be displayed."
        CatalogIssueKind.INTERNAL_FAILURE -> "The catalog is temporarily unavailable."
    }
