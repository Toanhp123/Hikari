package app.openstory.library.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.library.domain.LibraryFilter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun trueEmptyOffersBothRealExploreActions() {
        val explored = mutableListOf<CatalogMediaType>()
        setContent(
            state = HomeUiState(content = HomeContentState.LibraryEmpty),
            onExploreManga = { explored += CatalogMediaType.MANGA },
            onExploreLightNovels = { explored += CatalogMediaType.LIGHT_NOVEL },
        )

        composeRule.onNodeWithText("Your library is empty").assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.EXPLORE_MANGA).assertHasClickAction().performClick()
        composeRule.onNodeWithTag(HomeTestTags.EXPLORE_LIGHT_NOVELS).assertHasClickAction().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(CatalogMediaType.MANGA, CatalogMediaType.LIGHT_NOVEL), explored)
        }
    }

    @Test
    fun filteredEmptyDoesNotPretendTheWholeLibraryIsEmpty() {
        setContent(
            state = HomeUiState(
                inputQuery = "missing",
                filter = LibraryFilter.MANGA,
                content = HomeContentState.NoMatches,
            ),
        )

        composeRule.onNodeWithText("No saved stories match").assertIsDisplayed()
        composeRule.onNodeWithText("Your library is empty").assertDoesNotExist()
        composeRule.onNodeWithTag(HomeTestTags.EXPLORE_MANGA).assertDoesNotExist()
        composeRule.onNodeWithTag(HomeTestTags.EXPLORE_LIGHT_NOVELS).assertDoesNotExist()
    }

    @Test
    fun localFailureOffersRetryWithoutEmptyExploreActions() {
        var retries = 0
        setContent(
            state = HomeUiState(content = HomeContentState.Failure),
            onRetry = { retries += 1 },
        )

        composeRule.onNodeWithText("Library unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.onNodeWithTag(HomeTestTags.EXPLORE_MANGA).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun searchFiltersAndPosterSelectionDispatchFeatureIntents() {
        val queries = mutableListOf<String>()
        val filters = mutableListOf<LibraryFilter>()
        val selected = mutableListOf<StorySourceRef>()
        val story = poster("saved-story")
        setContent(
            state = HomeUiState(content = HomeContentState.Content(listOf(story))),
            onInputQueryChanged = queries::add,
            onFilterSelected = filters::add,
            onStorySelected = { selected += it.ref },
        )

        composeRule.onNodeWithTag(HomeTestTags.SEARCH).performTextInput("saved")
        composeRule.onNode(hasText("Light Novel") and isSelectable()).performClick()
        composeRule.onNodeWithTag(HomeTestTags.story(story.ref)).assertHasClickAction().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("saved"), queries)
            assertEquals(listOf(LibraryFilter.LIGHT_NOVEL), filters)
            assertEquals(listOf(story.ref), selected)
        }
        composeRule.onAllNodes(hasText("All") and isSelectable()).assertCountEquals(1)
        composeRule.onAllNodes(hasText("Manga") and isSelectable()).assertCountEquals(1)
        composeRule.onAllNodes(hasText("Light Novel") and isSelectable()).assertCountEquals(1)
    }

    private fun setContent(
        state: HomeUiState,
        onInputQueryChanged: (String) -> Unit = {},
        onFilterSelected: (LibraryFilter) -> Unit = {},
        onExploreManga: () -> Unit = {},
        onExploreLightNovels: () -> Unit = {},
        onStorySelected: (LibraryStoryPosterUi) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                var currentState by remember(state) { mutableStateOf(state) }
                HomeScreen(
                    state = currentState,
                    onInputQueryChanged = { inputQuery ->
                        currentState = currentState.copy(inputQuery = inputQuery)
                        onInputQueryChanged(inputQuery)
                    },
                    onFilterSelected = { filter ->
                        currentState = currentState.copy(filter = filter)
                        onFilterSelected(filter)
                    },
                    onExploreManga = onExploreManga,
                    onExploreLightNovels = onExploreLightNovels,
                    onStorySelected = onStorySelected,
                    onRetry = onRetry,
                    artwork = { _, modifier ->
                        androidx.compose.foundation.layout.Box(
                            modifier.fillMaxSize().background(Color.LightGray),
                        )
                    },
                )
            }
        }
    }

    private companion object {
        val SOURCE = CatalogSourceKey("home-screen-test")

        fun poster(id: String): LibraryStoryPosterUi {
            val ref = StorySourceRef(
                storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE, id)),
                catalogSourceKey = SOURCE,
                sourceStoryId = id,
            )
            return LibraryStoryPosterUi(
                ref = ref,
                originMediaContext = CatalogMediaType.MANGA,
                savedAtEpochMs = 20,
                title = "Saved Story",
                coverAssetKey = null,
                coverLocator = null,
                supportingText = "Manga",
            )
        }
    }
}
