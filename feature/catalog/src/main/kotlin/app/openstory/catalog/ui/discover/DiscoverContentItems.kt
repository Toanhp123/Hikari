package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.discoverContentItems(
    state: DiscoverUiState,
    onRefresh: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onContentTypeSelected: (ContentType) -> Unit,
    mediaTypeFocusRequester: FocusRequester?,
    mediaTypeNextFocusRequester: FocusRequester?,
) {
    if (state.loading) {
        item(
            key = "discover-loading",
            contentType = "discover-loading",
        ) {
            DiscoverLoadingContent()
        }
        return
    }

    discoverPopularItem(state, onStorySelected)
    discoverMediaSelectorItem(
        state = state,
        onContentTypeSelected = onContentTypeSelected,
        mediaTypeFocusRequester = mediaTypeFocusRequester,
        mediaTypeNextFocusRequester = mediaTypeNextFocusRequester,
    )
    discoverLatestItems(
        items = state.latestUpdates,
        onSelected = onStorySelected,
        separatedFromPreviousSection = true,
    )
    discoverTopRatedItems(
        items = state.topRated,
        onSelected = onStorySelected,
        separatedFromPreviousSection = true,
    )
    discoverFeedbackItems(
        state = state,
        onRefresh = onRefresh,
        separatedFromPreviousSection = true,
    )
    discoverEmptyItem(state)
}

private fun LazyListScope.discoverPopularItem(
    state: DiscoverUiState,
    onStorySelected: (StoryId) -> Unit,
) {
    if (state.popular.isEmpty()) return
    item(
        key = "discover-popular",
        contentType = "discover-popular",
    ) {
        HikariSectionLead(
            header = { HikariSectionHeader(title = "POPULAR") },
            firstContent = {
                DiscoverPopularPager(
                    stories = state.popular,
                    selectedContentType = state.selectedContentType,
                    onSelected = onStorySelected,
                )
            },
        )
    }
}

private fun LazyListScope.discoverMediaSelectorItem(
    state: DiscoverUiState,
    onContentTypeSelected: (ContentType) -> Unit,
    mediaTypeFocusRequester: FocusRequester?,
    mediaTypeNextFocusRequester: FocusRequester?,
) {
    item(
        key = "discover-media-selector",
        contentType = "discover-media-selector",
    ) {
        val selectorModifier = if (state.popular.isNotEmpty()) {
            Modifier.padding(
                top = MaterialTheme.hikariSpacing.sectionGap - MaterialTheme.hikariSpacing.itemGap,
            )
        } else {
            Modifier
        }
        DiscoverMediaTypeSelector(
            options = state.mediaTypeOptions,
            selectedContentType = state.selectedContentType,
            onSelected = onContentTypeSelected,
            modifier = selectorModifier,
            focusRequester = mediaTypeFocusRequester,
            nextFocusRequester = mediaTypeNextFocusRequester,
        )
    }
}

private fun LazyListScope.discoverEmptyItem(state: DiscoverUiState) {
    if (state.hasContent) return
    item(
        key = "discover-empty",
        contentType = "discover-empty",
    ) {
        DiscoverEmptyContent(
            modifier = Modifier
                .fillParentMaxSize()
                .padding(
                    top = MaterialTheme.hikariSpacing.sectionGap - MaterialTheme.hikariSpacing.itemGap,
                ),
        )
    }
}
