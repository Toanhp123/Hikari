package app.openstory.catalog.feature.discover.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.discover.DiscoverCardUi
import app.openstory.catalog.feature.discover.DiscoverContentState
import app.openstory.catalog.feature.discover.DiscoverIssueKind
import app.openstory.catalog.feature.discover.DiscoverIssueUi
import app.openstory.catalog.feature.discover.DiscoverSectionLabels
import app.openstory.catalog.feature.discover.DiscoverTestTags
import app.openstory.catalog.feature.discover.DiscoverUiState
import app.openstory.catalog.feature.discover.editorial.EditorialHeroBanner
import app.openstory.catalog.feature.discover.header.DiscoverHeader
import app.openstory.catalog.feature.discover.section.discoverSections
import app.openstory.catalog.feature.discover.section.loadingSections
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.theme.HikariBreakpoints
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun DiscoverScreen(
    mediaType: CatalogMediaType,
    state: DiscoverUiState,
    listState: LazyListState,
    sectionLabels: DiscoverSectionLabels,
    onStorySelected: (DiscoverCardUi) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onCoverReady: () -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalInset = HikariBreakpoints.screenHorizontalInset(maxWidth)
        val layout = DiscoverLayoutMetrics(
            horizontalInset = horizontalInset,
            bottomReserve = MaterialTheme.hikariSpacing.space32,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            DiscoverFeed(
                mediaType = mediaType,
                state = state,
                listState = listState,
                layout = layout,
                sectionLabels = sectionLabels,
                onStorySelected = onStorySelected,
                onRefresh = onRefresh,
                onRetry = onRetry,
                onCoverReady = onCoverReady,
            )
        }
    }
}

@Composable
private fun DiscoverFeed(
    mediaType: CatalogMediaType,
    state: DiscoverUiState,
    listState: LazyListState,
    layout: DiscoverLayoutMetrics,
    sectionLabels: DiscoverSectionLabels,
    onStorySelected: (DiscoverCardUi) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onCoverReady: () -> Unit,
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
                DiscoverHeader(mediaType, layout.horizontalInset)
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
                sectionLabels = sectionLabels,
                onStorySelected = onStorySelected,
                onRetry = onRetry,
                onCoverReady = onCoverReady,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.discoverContent(
    content: DiscoverContentState,
    horizontalInset: Dp,
    sectionLabels: DiscoverSectionLabels,
    onStorySelected: (DiscoverCardUi) -> Unit,
    onRetry: () -> Unit,
    onCoverReady: () -> Unit,
) {
    when (content) {
        DiscoverContentState.NoContentLoading -> loadingSections(horizontalInset, sectionLabels)
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
            discoverSections(content.sections, horizontalInset, sectionLabels, onStorySelected, onCoverReady)
        }
    }
}

@Composable
private fun DiscoverIssuePanel(
    issue: DiscoverIssueUi,
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
    issue: DiscoverIssueUi,
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

private val DiscoverIssueKind.message: String
    get() = when (this) {
        DiscoverIssueKind.SOURCE_UNAVAILABLE -> "No catalog source is available in this build."
        DiscoverIssueKind.INVALID_SOURCE_DATA -> "The source returned data that could not be safely displayed."
        DiscoverIssueKind.ACQUISITION_FAILED -> "The catalog could not be refreshed."
        DiscoverIssueKind.STORAGE_FAILED -> "Saved catalog data could not be read."
        DiscoverIssueKind.ARTWORK_FAILED -> "Some artwork could not be displayed."
        DiscoverIssueKind.INTERNAL_FAILURE -> "The catalog is temporarily unavailable."
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
