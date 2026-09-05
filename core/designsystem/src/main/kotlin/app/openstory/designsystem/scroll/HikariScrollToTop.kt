package app.openstory.designsystem.scroll

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun rememberHikariScrollToTopAction(scrollToTop: suspend () -> Unit): () -> Unit {
    val coroutineScope = rememberCoroutineScope()
    val currentScrollToTop = rememberUpdatedState(scrollToTop)
    val controller = remember { ScrollToTopController() }
    return remember(coroutineScope, controller) {
        {
            controller.job?.cancel()
            controller.job = coroutineScope.launch { currentScrollToTop.value() }
        }
    }
}

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

private class ScrollToTopController {
    var job: Job? = null
}
