package app.openstory.catalog.feature.discover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.feature.state.CatalogIssueUi
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.theme.hikariSpacing
import kotlinx.coroutines.launch

@Composable
internal fun DiscoverScreen(
    state: DiscoverUiState,
    listState: LazyListState,
    onMediaSelected: (CatalogMediaType) -> Unit,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalInset = if (maxWidth >= DiscoverVisualMetrics.WideLayoutThreshold) {
            MaterialTheme.hikariSpacing.space32
        } else {
            MaterialTheme.hikariSpacing.space20
        }
        val layout = DiscoverLayoutMetrics(
            horizontalInset = horizontalInset,
            bottomReserve = DiscoverVisualMetrics.MediaNavHeight +
                MaterialTheme.hikariSpacing.space32 +
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        )

        Box(modifier = Modifier.fillMaxSize()) {
            DiscoverFeed(
                state = state,
                listState = listState,
                layout = layout,
                onStorySelected = onStorySelected,
                onRefresh = onRefresh,
                onRetry = onRetry,
            )
            DiscoverMediaNavOverlay(
                selectedMediaType = state.selectedMediaType,
                onMediaSelected = { mediaType ->
                    coroutineScope.launch {
                        listState.scrollToItem(0)
                        onMediaSelected(mediaType)
                    }
                },
                onHomeSelected = {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                },
            )
        }
    }
}

@Composable
private fun DiscoverFeed(
    state: DiscoverUiState,
    listState: LazyListState,
    layout: DiscoverLayoutMetrics,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    HikariPullToRefresh(
        refreshing = state.content.refreshing,
        enabled = state.content.hasDurableContent,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag(DiscoverTestTags.ROOT),
            contentPadding = PaddingValues(bottom = layout.bottomReserve),
        ) {
            item(key = "discover-header") {
                DiscoverHeader(state.selectedMediaType, layout.horizontalInset)
            }
            item(key = "discover-editorial-banner") {
                EditorialHeroBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = layout.horizontalInset,
                            vertical = MaterialTheme.hikariSpacing.space16,
                        ),
                )
            }
            item(key = "discover-header-gap") {
                Spacer(Modifier.height(MaterialTheme.hikariSpacing.space16))
            }
            discoverContent(
                content = state.content,
                horizontalInset = layout.horizontalInset,
                onStorySelected = onStorySelected,
                onRetry = onRetry,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.discoverContent(
    content: DiscoverContentState,
    horizontalInset: Dp,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
    onRetry: () -> Unit,
) {
    when (content) {
        DiscoverContentState.NoContentLoading -> loadingSections(horizontalInset)
        is DiscoverContentState.NoContentFailure -> item(key = "discover-failure") {
            DiscoverFailureState(content.issue, onRetry, horizontalInset)
        }
        is DiscoverContentState.Empty -> {
            content.issue?.let { issue ->
                item(key = "discover-empty-issue") {
                    DiscoverIssuePanel(issue, onRetry, horizontalInset)
                }
            }
            item(key = "discover-empty") { DiscoverEmptyState(horizontalInset) }
        }
        is DiscoverContentState.Content -> {
            content.issue?.let { issue ->
                item(key = "discover-content-issue") {
                    DiscoverIssuePanel(issue, onRetry, horizontalInset)
                }
            }
            discoverSections(content.sections, horizontalInset, onStorySelected)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.DiscoverMediaNavOverlay(
    selectedMediaType: CatalogMediaType,
    onMediaSelected: (CatalogMediaType) -> Unit,
    onHomeSelected: () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = MaterialTheme.hikariSpacing.space16),
        contentAlignment = Alignment.Center,
    ) {
        CatalogMediaDestinationNav(
            selectedMediaType = selectedMediaType,
            onMediaSelected = onMediaSelected,
            onHomeSelected = onHomeSelected,
        )
    }
}

@Composable
private fun DiscoverIssuePanel(
    issue: CatalogIssueUi,
    onRetry: () -> Unit,
    horizontalInset: Dp,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalInset),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        HikariInlineFeedback(
            message = issue.kind.message,
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
            actionLabel = "Try again".takeIf { issue.retryable },
            onAction = onRetry.takeIf { issue.retryable },
        )
    }
}

@Composable
private fun DiscoverFailureState(
    issue: CatalogIssueUi,
    onRetry: () -> Unit,
    horizontalInset: Dp,
) {
    HikariErrorState(
        title = issue.kind.message,
        modifier = Modifier.padding(horizontal = horizontalInset),
        actionLabel = "Try again".takeIf { issue.retryable },
        onAction = onRetry.takeIf { issue.retryable },
    )
}

@Composable
private fun DiscoverEmptyState(horizontalInset: Dp) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalInset)
            .testTag(DiscoverTestTags.EMPTY),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        HikariEmptyState(
            title = "Nothing published yet",
            body = "This source completed successfully, but has no eligible stories for this medium.",
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space24),
        )
    }
}

private val CatalogIssueKind.message: String
    get() = when (this) {
        CatalogIssueKind.SOURCE_UNAVAILABLE -> "No catalog source is available in this build."
        CatalogIssueKind.INVALID_SOURCE_DATA -> "The source returned data that could not be safely displayed."
        CatalogIssueKind.ACQUISITION_FAILED -> "The catalog could not be refreshed."
        CatalogIssueKind.STORAGE_FAILED -> "Saved catalog data could not be read."
        CatalogIssueKind.ARTWORK_FAILED -> "Some artwork could not be displayed."
        CatalogIssueKind.INTERNAL_FAILURE -> "The catalog is temporarily unavailable."
    }

private val DiscoverContentState.hasDurableContent: Boolean
    get() = this is DiscoverContentState.Empty || this is DiscoverContentState.Content

private val DiscoverContentState.refreshing: Boolean
    get() = when (this) {
        is DiscoverContentState.Empty -> refreshing
        is DiscoverContentState.Content -> refreshing
        else -> false
    }

private data class DiscoverLayoutMetrics(
    val horizontalInset: Dp,
    val bottomReserve: Dp,
)
