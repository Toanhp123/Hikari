package app.openstory.catalog.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.library.LibraryStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class LibraryTopLevelChromeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun titleAndToolbarStayPinnedAndBackToTopReturnsToFirstStory() {
        compose.setContent {
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
                    contentPadding = PaddingValues(top = 24.dp, bottom = 92.dp),
                )
            }
        }

        compose.onNodeWithTag("library-collection").performScrollToIndex(7)
        compose.onNodeWithText("Library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search your Library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back to top").assertDoesNotExist()
    }
}

private fun libraryState(): LibraryUiState {
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
        items = items,
        totalCount = items.size,
        statusCounts = mapOf(LibraryStatus.WANT_TO_READ to items.size),
        displayMode = LibraryDisplayMode.LIST,
        loading = false,
    )
}
