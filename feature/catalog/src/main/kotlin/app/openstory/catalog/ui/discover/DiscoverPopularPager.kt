package app.openstory.catalog.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.hikariColors
import app.openstory.designsystem.theme.hikariOpacity
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun DiscoverPopularPager(
    stories: List<DiscoverStoryItem>,
    selectedContentType: ContentType,
    onSelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stories.isEmpty()) return
    val pages = stories.take(MAX_POPULAR_PAGES)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    var previousContentType by remember { mutableStateOf(selectedContentType) }
    var visibleStoryId by remember { mutableStateOf(pages.first().storyId) }

    LaunchedEffect(selectedContentType, pages.map { it.storyId }) {
        if (selectedContentType != previousContentType) {
            previousContentType = selectedContentType
            visibleStoryId = pages.first().storyId
            pagerState.scrollToPage(0)
        } else {
            val retainedIndex = pages.indexOfFirst { it.storyId == visibleStoryId }
            val target = if (retainedIndex >= 0) {
                retainedIndex
            } else {
                pagerState.currentPage.coerceIn(0, pages.lastIndex)
            }
            pagerState.scrollToPage(target)
            visibleStoryId = pages[target].storyId
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        pages.getOrNull(pagerState.currentPage)?.let { visibleStoryId = it.storyId }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discover-popular-pager"),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val item = pages[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            "Popular story ${page + 1} of ${pages.size}: ${item.title}"
                    },
            ) {
                DiscoverHero(
                    item = item,
                    onSelected = onSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(MaterialTheme.hikariSpacing.space12)
                    .testTag("discover-popular-page-indicator"),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
            ) {
                pages.indices.forEach { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .size(MaterialTheme.hikariSpacing.space8)
                            .background(
                                color = if (selected) {
                                    MaterialTheme.hikariColors.onArtwork
                                } else {
                                    MaterialTheme.hikariColors.onArtwork.copy(
                                        alpha = MaterialTheme.hikariOpacity.onArtworkSecondary,
                                    )
                                },
                                shape = MaterialTheme.hikariShapes.circle,
                            ),
                    )
                }
            }
        }
    }
}

private const val MAX_POPULAR_PAGES = 5
