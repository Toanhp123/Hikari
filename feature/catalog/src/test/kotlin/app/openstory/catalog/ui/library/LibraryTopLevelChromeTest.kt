package app.openstory.catalog.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.library.LibraryStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class LibraryTopLevelChromeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun titleAndToolbarStayPinnedAndBackToTopReturnsToFirstStory() {
        lateinit var listState: LazyListState
        compose.setContent {
            listState = rememberLazyListState()
            HikariTheme {
                LibraryScreen(
                    state = libraryState(),
                    onQueryChange = {},
                    onStatusSelected = {},
                    onSourceFilterSelected = {},
                    onSortSelected = {},
                    onDisplayModeSelected = {},
                    onClearFilters = {},
                    onDiscover = {},
                    onStorySelected = {},
                    listState = listState,
                    contentPadding = PaddingValues(top = 24.dp, bottom = 92.dp),
                )
            }
        }

        compose.onNodeWithTag("library-collection").performScrollToIndex(7)
        compose.onNodeWithText("Library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search your Library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun gridBackToTopReturnsToExactStart() {
        lateinit var gridState: LazyGridState
        compose.setContent {
            gridState = rememberLazyGridState()
            HikariTheme {
                LibraryScreen(
                    state = libraryState(LibraryDisplayMode.GRID),
                    onQueryChange = {},
                    onStatusSelected = {},
                    onSourceFilterSelected = {},
                    onSortSelected = {},
                    onDisplayModeSelected = {},
                    onClearFilters = {},
                    onDiscover = {},
                    onStorySelected = {},
                    gridState = gridState,
                )
            }
        }

        compose.onNodeWithTag("library-collection").performScrollToIndex(10)
        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, gridState.firstVisibleItemIndex)
            assertEquals(0, gridState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun backToTopIsVisibleForAFirstItemOffset() {
        compose.setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemScrollOffset = 24)
            HikariTheme {
                LibraryScreen(
                    state = libraryState(),
                    onQueryChange = {},
                    onStatusSelected = {},
                    onSourceFilterSelected = {},
                    onSortSelected = {},
                    onDisplayModeSelected = {},
                    onClearFilters = {},
                    onDiscover = {},
                    onStorySelected = {},
                    listState = listState,
                )
            }
        }

        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed()
    }
}

private fun libraryState(displayMode: LibraryDisplayMode = LibraryDisplayMode.LIST): LibraryUiState {
    val items = (0..11).map { index ->
        LibraryItemUiModel(
            storyId = StoryId("story-$index"),
            title = "Story $index",
            contentType = ContentType.WEB_NOVEL,
            coverUrl = null,
            status = LibraryStatus.WANT_TO_READ,
            sourceState = LibrarySourceState.NO_MAPPING,
            progressFraction = null,
            addedAt = index.toLong(),
            updatedAt = index.toLong(),
        )
    }
    return LibraryUiState(
        content = ContentState.Ready(
            LibraryContent(
                totalCount = items.size,
                statusCounts = mapOf(LibraryStatus.WANT_TO_READ to items.size),
                collection = LibraryCollectionState.Ready(items),
            ),
        ),
        displayMode = displayMode,
    )
}
