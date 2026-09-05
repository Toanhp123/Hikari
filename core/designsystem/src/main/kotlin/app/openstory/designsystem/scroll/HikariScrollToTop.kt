package app.openstory.designsystem.scroll

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

suspend fun LazyListState.hikariScrollToTop() {
    if (firstVisibleItemIndex > SCROLL_TO_TOP_STAGING_INDEX) {
        scrollToItem(SCROLL_TO_TOP_STAGING_INDEX)
    }
    animateScrollToItem(0)
}

suspend fun LazyGridState.hikariScrollToTop() {
    if (firstVisibleItemIndex > SCROLL_TO_TOP_STAGING_INDEX) {
        scrollToItem(SCROLL_TO_TOP_STAGING_INDEX)
    }
    animateScrollToItem(0)
}

private const val SCROLL_TO_TOP_STAGING_INDEX = 3
