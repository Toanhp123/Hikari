package app.openstory.catalog.ui.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.search.CatalogSearchFailure
import app.openstory.catalog.search.CatalogSearchFilterGroup
import app.openstory.catalog.search.CatalogSearchResult
import app.openstory.catalog.search.CatalogSearchSourceCard
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.source.SourceFilterOption
import app.openstory.catalog.source.SourceOptionFilter
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun searchResults() = capture(fixture(), "search.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun partialSourceFailure() = capture(
        fixtureWithResult { result ->
            result.copy(failures = listOf(CatalogSearchFailure(PluginId("catalog.b"), "catalog.offline", true)))
        },
        "partial-source-failure.png",
    )

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun idleShortQueryDoesNotClaimEmptyResults() {
        setContent(SearchUiState(query = "a", resultState = SearchResultState.Idle))

        compose.onNodeWithText("No matches found").assertDoesNotExist()
        compose.onNodeWithTag("search-progress").assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun pendingRequestShowsProgressWithoutEmptyState() {
        setContent(
            SearchUiState(
                query = "novel",
                resultState = SearchResultState.Active(ContentState.Pending),
            ),
        )

        compose.onNodeWithTag("search-progress").assertIsDisplayed()
        compose.onNodeWithText("No matches found").assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun authoritativeReadyEmptyShowsEmptyState() {
        setContent(
            SearchUiState(
                query = "novel",
                resultState = SearchResultState.Active(
                    ContentState.Ready(CatalogSearchResult(emptyList(), emptyList())),
                ),
            ),
        )

        compose.onNodeWithText("No matches found").assertIsDisplayed()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun blockingSearchFailureRetriesSearchWithoutMasqueradingFilterIssue() {
        var retried = false
        compose.setContent {
            HikariTheme(darkTheme = true) {
                SearchScreen(
                    state = SearchUiState(
                        query = "novel",
                        filterIssue = CatalogUiFailure("catalog.search.filters_exception", false),
                        resultState = SearchResultState.Active(
                            ContentState.Failed(CatalogUiFailure("catalog.search.exception", true)),
                        ),
                    ),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { _, _, _ -> },
                    onClearFilters = {},
                    onStorySelected = {},
                    onRetrySearch = { retried = true },
                )
            }
        }

        compose.onNodeWithText("Search unavailable").assertIsDisplayed()
        compose.onNodeWithText("Filters unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun selectionIssueDoesNotReplaceReadySearchContent() {
        setContent(
            fixture().copy(
                selectionIssue = CatalogUiFailure("catalog.search.selection_failed", retryable = true),
            ),
        )

        compose.onNodeWithText("Couldn't open story").assertIsDisplayed()
        compose.onNodeWithText("The Fox of the Moonlit Archive").assertIsDisplayed()
        compose.onNodeWithText("Search unavailable").assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun focusedHeaderRespectsProvidedSafeInsetAndNavigatesBack() {
        var backRequested = false
        compose.setContent {
            HikariTheme(darkTheme = true) {
                SearchScreen(
                    state = fixture(),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { _, _, _ -> },
                    onClearFilters = {},
                    onStorySelected = {},
                    onBack = { backRequested = true },
                    contentPadding = PaddingValues(top = 40.dp),
                )
            }
        }

        val content = compose.onNodeWithTag("search-content")
        assertTrue(
            content.fetchSemanticsNode().boundsInRoot.top >= 40f,
            "Search scroll viewport must begin below shell-provided top safe inset",
        )
        val back = compose.onNodeWithContentDescription("Back")
        assertTrue(back.fetchSemanticsNode().boundsInRoot.top >= 40f)
        back.performClick()
        assertTrue(backRequested)
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun deepSearchScrollShowsBackToTopAndReturnsToStart() {
        lateinit var listState: LazyListState
        compose.setContent {
            listState = rememberLazyListState()
            HikariTheme(darkTheme = true) {
                SearchScreen(
                    state = scrollFixture(),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { _, _, _ -> },
                    onClearFilters = {},
                    onStorySelected = {},
                    listState = listState,
                )
            }
        }

        val content = compose.onNodeWithTag("search-content")
        content.performScrollToIndex(20)
        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun searchBackToTopIsVisibleForAFirstItemOffset() {
        compose.setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemScrollOffset = 24)
            HikariTheme(darkTheme = true) {
                SearchScreen(
                    state = scrollFixture(),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { _, _, _ -> },
                    onClearFilters = {},
                    onStorySelected = {},
                    listState = listState,
                )
            }
        }

        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun backTitleAndSearchStayPinnedWhileGuidanceScrollsAway() {
        compose.setContent {
            HikariTheme(darkTheme = true) {
                SearchScreen(
                    state = scrollFixture(),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { _, _, _ -> },
                    onClearFilters = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithTag("search-content").performScrollToIndex(20)
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search stories").assertIsDisplayed()
        compose.onNodeWithText("Find your next story").assertDoesNotExist()
    }

    private fun setContent(state: SearchUiState) {
        compose.setContent {
            HikariTheme(darkTheme = true) {
                SearchScreen(state, {}, {}, { _, _, _ -> }, {}, {})
            }
        }
    }

    private fun capture(state: SearchUiState, fileName: String) {
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                SearchScreen(state, {}, {}, { _, _, _ -> }, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/search/$fileName")
    }
}

private fun scrollFixture(): SearchUiState {
    val base = fixture()
    val ready = base.readyResult()
    val story = ready.stories.single()
    return base.copy(
        resultState = SearchResultState.Active(
            ContentState.Ready(
                ready.copy(
                    stories = (1..30).map { index ->
                        story.copy(story = story.story.copy(id = StoryId("moonlit-$index")))
                    },
                ),
            ),
        ),
    )
}

private fun fixture(): SearchUiState {
    val plugin = PluginId("catalog.mangadex")
    val source = CatalogSearchSourceCard(
        plugin, "moonlit", "The Fox of the Moonlit Archive", ContentType.MANGA,
        setOf("Mira Hoshino"), null, Score(8.8, 10.0),
    )
    return SearchUiState(
        query = "moonlit archive",
        filterGroups = listOf(
            CatalogSearchFilterGroup(
                plugin,
                listOf(
                    SourceOptionFilter(
                        "language", "Language", true,
                        listOf(SourceFilterOption("en", "English"), SourceFilterOption("vi", "Vietnamese")),
                    ),
                    SourceOptionFilter(
                        "genre", "Genre", true,
                        listOf(SourceFilterOption("fantasy", "Fantasy"), SourceFilterOption("mystery", "Mystery")),
                    ),
                ),
            ),
        ),
        filterValues = mapOf(plugin to mapOf("language" to listOf("en"))),
        resultState = SearchResultState.Active(
            ContentState.Ready(
                CatalogSearchResult(
                    stories = listOf(
                        CatalogSearchStory(
                            Story(StoryId("moonlit"), ContentType.MANGA),
                            CatalogStoryProjection(
                                StoryId("moonlit"),
                                "The Fox of the Moonlit Archive",
                                ContentType.MANGA,
                                null,
                                authors = setOf("Mira Hoshino"),
                            ),
                            listOf(source),
                        ),
                    ),
                    failures = emptyList(),
                ),
            ),
        ),
        recentQueries = listOf("quiet stars", "winter index"),
    )
}

private fun fixtureWithResult(
    transform: (CatalogSearchResult) -> CatalogSearchResult,
): SearchUiState {
    val state = fixture()
    val ready = state.readyResult()
    return state.copy(resultState = SearchResultState.Active(ContentState.Ready(transform(ready))))
}

private fun SearchUiState.readyResult(): CatalogSearchResult =
    ((resultState as SearchResultState.Active).content as ContentState.Ready<CatalogSearchResult>).value
