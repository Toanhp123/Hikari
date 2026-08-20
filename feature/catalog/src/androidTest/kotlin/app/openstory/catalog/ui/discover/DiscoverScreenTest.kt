package app.openstory.catalog.ui.discover

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Rule
import org.junit.Test

class DiscoverScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun semanticHierarchyRendersWithoutSourceControls() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = state(),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithTag("discover-popular-pager").assertIsDisplayed()
        compose.onNodeWithTag("discover-media-selector").assertIsDisplayed()
        compose.onNodeWithText("All sources").assertDoesNotExist()
        compose.onNodeWithText("Light Novel").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Popular story 1 of 5: Story 1").assertIsDisplayed()

        compose.onNodeWithTag("discover-list").performScrollToIndex(2)
        compose.onNodeWithTag("discover-latest-grid").assertIsDisplayed()
        compose.onNodeWithTag("discover-list").performScrollToIndex(5)
        compose.onNodeWithTag("discover-top-rated").assertIsDisplayed()
    }

    @Test
    fun semanticLimitsAreAppliedByPresentation() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = state(
                        latest = (1..10).map(::story),
                        top = (1..6).map(::story),
                    ),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithTag("discover-list").performScrollToIndex(4)
        compose.onNodeWithTag("discover-latest-item-story-9", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("discover-latest-item-story-10", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("discover-list").performScrollToIndex(9)
        compose.onNodeWithTag("discover-top-rated-rank-5", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("discover-top-rated-rank-6", useUnmergedTree = true).assertDoesNotExist()
    }
}

private fun state(
    latest: List<DiscoverStoryItem> = (1..9).map(::story),
    top: List<DiscoverStoryItem> = (1..5).map(::story),
) = DiscoverUiState(
    popular = (1..5).map(::story),
    latestUpdates = latest,
    topRated = top,
    loading = false,
)

private fun story(index: Int) = DiscoverStoryItem(
    storyId = StoryId("story-$index"),
    title = "Story $index",
    coverUrl = null,
    contentType = ContentType.MANGA,
    score = Score(8.0 + index / 10.0, 10.0),
    genres = listOf("Action", "Fantasy"),
    publicationStatus = PublicationStatus.ONGOING,
    latestUpdate = CatalogLatestUpdate(1_000L + index, index.toString()),
)
