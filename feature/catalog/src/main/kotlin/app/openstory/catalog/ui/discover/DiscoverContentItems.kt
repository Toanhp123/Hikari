package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.discoverContentItems(
    state: DiscoverUiState,
    onRefresh: () -> Unit,
    onRetryContent: () -> Unit,
    onRetryObservation: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onContentTypeSelected: (ContentType) -> Unit,
    mediaTypeFocusRequester: FocusRequester?,
    mediaTypeNextFocusRequester: FocusRequester?,
) {
    when (val contentState = state.content) {
        ContentState.Pending -> {
            item(
                key = "discover-loading",
                contentType = "discover-loading",
            ) {
                DiscoverLoadingContent()
            }
            return
        }
        is ContentState.Failed -> {
            item(
                key = "discover-error",
                contentType = "discover-error",
            ) {
                HikariErrorState(
                    title = "Discover unavailable",
                    message = catalogFailureMessage(
                        contentState.failure.code,
                        "Couldn't load Discover.",
                    ),
                    actionLabel = if (contentState.failure.retryable) "Retry" else null,
                    onAction = if (contentState.failure.retryable) onRetryContent else null,
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
            return
        }
        is ContentState.Ready -> {
            val content = contentState.value
            discoverPopularItem(content, onStorySelected)
            discoverMediaSelectorItem(
                content = content,
                onContentTypeSelected = onContentTypeSelected,
                mediaTypeFocusRequester = mediaTypeFocusRequester,
                mediaTypeNextFocusRequester = mediaTypeNextFocusRequester,
            )
            discoverLatestItems(
                items = content.latestUpdates,
                onSelected = onStorySelected,
                separatedFromPreviousSection = true,
            )
            discoverTopRatedItems(
                items = content.topRated,
                onSelected = onStorySelected,
                separatedFromPreviousSection = true,
            )
            discoverFeedbackItems(
                state = state,
                onRefresh = onRefresh,
                onRetryObservation = onRetryObservation,
                separatedFromPreviousSection = true,
            )
            discoverEmptyItem(content)
        }
    }
}

private fun LazyListScope.discoverPopularItem(
    content: DiscoverContent,
    onStorySelected: (StoryId) -> Unit,
) {
    if (content.popular.isEmpty()) return
    item(
        key = "discover-popular",
        contentType = "discover-popular",
    ) {
        HikariSectionLead(
            header = { HikariSectionHeader(title = "POPULAR") },
            firstContent = {
                DiscoverPopularPager(
                    stories = content.popular,
                    selectedContentType = content.selectedContentType,
                    onSelected = onStorySelected,
                )
            },
        )
    }
}

private fun LazyListScope.discoverMediaSelectorItem(
    content: DiscoverContent,
    onContentTypeSelected: (ContentType) -> Unit,
    mediaTypeFocusRequester: FocusRequester?,
    mediaTypeNextFocusRequester: FocusRequester?,
) {
    item(
        key = "discover-media-selector",
        contentType = "discover-media-selector",
    ) {
        val selectorModifier = if (content.popular.isNotEmpty()) {
            Modifier.padding(
                top = MaterialTheme.hikariSpacing.sectionGap - MaterialTheme.hikariSpacing.itemGap,
            )
        } else {
            Modifier
        }
        DiscoverMediaTypeSelector(
            options = content.mediaTypeOptions,
            selectedContentType = content.selectedContentType,
            onSelected = onContentTypeSelected,
            modifier = selectorModifier,
            focusRequester = mediaTypeFocusRequester,
            nextFocusRequester = mediaTypeNextFocusRequester,
        )
    }
}

private fun LazyListScope.discoverEmptyItem(content: DiscoverContent) {
    if (content.hasContent) return
    item(
        key = "discover-empty",
        contentType = "discover-empty",
    ) {
        DiscoverEmptyContent(
            reason = content.noContentReason ?: DiscoverNoContentReason.EMPTY_FEED,
            modifier = Modifier
                .fillParentMaxSize()
                .padding(
                    top = MaterialTheme.hikariSpacing.sectionGap - MaterialTheme.hikariSpacing.itemGap,
                ),
        )
    }
}
