package app.openstory.catalog.ui.discover

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
import app.openstory.catalog.model.Score
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class DiscoverTopLevelChromeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun searchHeaderStaysPinnedAndBackToTopReturnsToFirstItem() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(
                        popular = listOf(item(1)),
                        latestUpdates = (1..9).map(::item),
                        topRated = (10..14).map(::item),
                        loading = false,
                    ),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                    contentPadding = PaddingValues(top = 24.dp, bottom = 92.dp),
                )
            }
        }

        compose.onNodeWithText("HIKARI").assertDoesNotExist()
        compose.onNodeWithTag("discover-list").performScrollToIndex(5)
        compose.onNodeWithContentDescription("Search all stories").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back to top").assertDoesNotExist()
    }
}

private fun item(index: Int) = DiscoverStoryItem(
    storyId = StoryId("story-$index"),
    title = "Story $index",
    coverUrl = null,
    contentType = ContentType.MANGA,
    score = Score(8.0, 10.0),
    genres = listOf("Fantasy"),
    publicationStatus = null,
    latestUpdate = null,
)
