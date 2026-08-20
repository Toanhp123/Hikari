package app.openstory.catalog.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariSearchBar
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.HikariTopLevelScaffold
import app.openstory.designsystem.layout.withScreenContentInsets
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.theme.hikariAtmosphereBrush
import app.openstory.designsystem.theme.hikariBreakpoints
import app.openstory.designsystem.theme.hikariSpacing
import kotlinx.coroutines.launch

@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onContentTypeSelected: (ContentType) -> Unit,
    searchFocusRequester: FocusRequester? = null,
    searchNextFocusRequester: FocusRequester? = null,
    mediaTypeFocusRequester: FocusRequester? = null,
    mediaTypeNextFocusRequester: FocusRequester? = null,
    onUtilityRequested: () -> Unit = {},
    utilityFocusRequester: FocusRequester? = null,
    utilityNextFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val background = MaterialTheme.hikariAtmosphereBrush
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop = remember {
        derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_TO_TOP_ITEM_THRESHOLD }
    }
    val headerScrolled = remember {
        derivedStateOf { listState.canScrollBackward }
    }

    HikariDestinationScaffold(modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
        ) {
            HikariTopLevelScaffold(
                contentPadding = contentPadding,
                header = {
                    HikariTopLevelHeader(
                        onAction = onUtilityRequested,
                        focusRequester = utilityFocusRequester,
                        nextFocusRequester = utilityNextFocusRequester,
                        content = {
                            HikariSearchBar(
                                value = "",
                                onValueChange = {},
                                placeholder = "Search all stories",
                                contentDescription = "Search all stories",
                                readOnly = true,
                                onClick = onSearch,
                                modifier = Modifier.testTag("discover-search"),
                                focusRequester = searchFocusRequester,
                                nextFocusRequester = searchNextFocusRequester,
                            )
                        },
                    )
                },
                headerScrolled = headerScrolled.value,
                showScrollToTop = showScrollToTop.value,
                onScrollToTop = { coroutineScope.launch { listState.animateScrollToItem(0) } },
            ) { bodyPadding ->
                HikariPullToRefresh(
                    refreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("discover-pull-refresh"),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = MaterialTheme.hikariBreakpoints.medium)
                            .align(Alignment.TopCenter)
                            .testTag("discover-list"),
                        contentPadding = bodyPadding.withScreenContentInsets(),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                            MaterialTheme.hikariSpacing.itemGap,
                        ),
                    ) {
                        discoverContentItems(
                            state = state,
                            onRefresh = onRefresh,
                            onStorySelected = onStorySelected,
                            onContentTypeSelected = onContentTypeSelected,
                            mediaTypeFocusRequester = mediaTypeFocusRequester,
                            mediaTypeNextFocusRequester = mediaTypeNextFocusRequester,
                        )
                    }
                }
            }
        }
    }
}

private const val SCROLL_TO_TOP_ITEM_THRESHOLD = 3
