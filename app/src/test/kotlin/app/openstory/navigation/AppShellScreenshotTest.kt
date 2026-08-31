package app.openstory.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.ui.discover.DiscoverContent
import app.openstory.catalog.ui.discover.DiscoverMediaTypeOption
import app.openstory.catalog.ui.discover.DiscoverScreen
import app.openstory.catalog.ui.discover.DiscoverStoryItem
import app.openstory.catalog.search.CatalogSearchResult
import app.openstory.catalog.ui.discover.DiscoverUiState
import app.openstory.catalog.ui.search.SearchResultState
import app.openstory.catalog.ui.search.SearchScreen
import app.openstory.catalog.ui.search.SearchUiState
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.ui.HikariAppShell
import app.openstory.ui.HikariUtilitySheet
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppShellScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeSelectedDark() = captureShell(AppRoute.Home, darkTheme = true, "home-dark.png")

    @Test
    fun discoverSelectedLight() {
        compose.setContent {
            HikariTheme(darkTheme = false) {
                HikariAppShell(AppRoute.Discover, {}, {}) { contentPadding ->
                    DiscoverScreen(
                        state = discoverState(),
                        onRefresh = {},
                        onRetryContent = {},
                        onRetryObservation = {},
                        onSearch = {},
                        onStorySelected = {},
                        onContentTypeSelected = {},
                        contentPadding = contentPadding,
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/app-shell/discover-light.png")
    }

    @Test
    fun focusedSearchLight() {
        compose.setContent {
            HikariTheme(darkTheme = false) {
                HikariAppShell(AppRoute.Search, {}, {}) { contentPadding ->
                    SearchScreen(
                        state = SearchUiState(
                            query = "moonlit archive",
                            resultState = SearchResultState.Active(
                                ContentState.Ready(CatalogSearchResult(emptyList(), emptyList())),
                            ),
                        ),
                        onQueryChange = {},
                        onRecentSelected = {},
                        onFilterValuesChange = { _, _, _ -> },
                        onClearFilters = {},
                        onStorySelected = {},
                        contentPadding = contentPadding,
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/app-shell/search-focused-light.png")
    }

    @Test
    fun downloadsAndUpdatesUtilitySheet() {
        compose.setContent {
            HikariTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().background(Color(0xFF101417))) {
                    HikariUtilitySheet(onDismiss = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/app-shell/utility-sheet.png")
    }

    @Test
    fun discoverExposesMediaSelectionInsteadOfSourceSelection() {
        compose.setContent {
            HikariTheme {
                HikariAppShell(AppRoute.Discover, {}, {}) { contentPadding ->
                    DiscoverScreen(
                        state = discoverState(),
                        onRefresh = {},
                        onRetryContent = {},
                        onRetryObservation = {},
                        onSearch = {},
                        onStorySelected = {},
                        onContentTypeSelected = {},
                        contentPadding = contentPadding,
                    )
                }
            }
        }

        compose.onNodeWithText("Manga").assertIsSelected()
        compose.onNodeWithText("Light Novel").assertIsNotEnabled()
    }

    @Test
    fun discoverSearchAndUtilityShareTopBand() {
        compose.setContent {
            HikariTheme {
                HikariAppShell(AppRoute.Discover, {}, {}) { contentPadding ->
                    DiscoverScreen(
                        state = discoverEmptyState(),
                        onRefresh = {},
                        onRetryContent = {},
                        onRetryObservation = {},
                        onSearch = {},
                        onStorySelected = {},
                        onContentTypeSelected = {},
                        contentPadding = contentPadding,
                    )
                }
            }
        }

        val searchBounds = compose.onNodeWithContentDescription("Search all stories")
            .fetchSemanticsNode().boundsInRoot
        val utilityBounds = compose.onNodeWithContentDescription("Open quick access")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            searchBounds.top < utilityBounds.bottom && utilityBounds.top < searchBounds.bottom,
            "Search and utility controls should occupy the same top band",
        )
    }

    @Test
    fun shellLeavesTopLevelHeaderInsideContentFlow() {
        compose.setContent {
            HikariTheme {
                HikariAppShell(AppRoute.Home, {}, {}) { contentPadding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .testTag("shell-content-bounds"),
                    )
                }
            }
        }

        val bounds = compose.onNodeWithTag("shell-content-bounds").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top < 72f, "Shell must not reserve fixed top-level chrome")
        assertTrue(bounds.bottom <= 708f, "Shell must reserve its floating navigation chrome")
    }

    @Test
    fun keyboardFocusMovesSearchToUtilityThenMediaSelector() {
        val searchFocus = FocusRequester()
        val utilityFocus = FocusRequester()
        val mediaFocus = FocusRequester()
        lateinit var inputModeManager: InputModeManager
        compose.setContent {
            HikariTheme {
                inputModeManager = LocalInputModeManager.current
                HikariAppShell(
                    currentRoute = AppRoute.Discover,
                    onTopLevelSelected = {},
                    onUtilityRequested = {},
                    utilityFocusRequester = utilityFocus,
                    utilityNextFocusRequester = mediaFocus,
                ) { contentPadding ->
                    DiscoverScreen(
                        state = discoverState(),
                        onRefresh = {},
                        onRetryContent = {},
                        onRetryObservation = {},
                        onSearch = {},
                        onStorySelected = {},
                        onContentTypeSelected = {},
                        searchFocusRequester = searchFocus,
                        searchNextFocusRequester = utilityFocus,
                        mediaTypeFocusRequester = mediaFocus,
                        onUtilityRequested = {},
                        utilityFocusRequester = utilityFocus,
                        utilityNextFocusRequester = mediaFocus,
                        contentPadding = contentPadding,
                    )
                }
            }
        }

        val search = compose.onNodeWithContentDescription("Search all stories")
        val utility = compose.onNodeWithContentDescription("Open quick access")
        val manga = compose.onNodeWithText("Manga")

        compose.runOnIdle {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            searchFocus.requestFocus()
        }
        search.assertIsFocused()
        search.performKeyInput { pressKey(Key.Tab) }
        utility.assertIsFocused()
        utility.performKeyInput { pressKey(Key.Tab) }
        manga.assertIsFocused()
    }

    private fun captureShell(route: AppRoute, darkTheme: Boolean, fileName: String) {
        compose.setContent {
            HikariTheme(darkTheme = darkTheme) {
                HikariAppShell(
                    currentRoute = route,
                    onTopLevelSelected = {},
                    onUtilityRequested = {},
                ) { contentPadding ->
                    Box(Modifier.fillMaxSize().background(Color(0xFF315F74))) {
                        Text(route.toString(), Modifier.padding(contentPadding))
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/app-shell/$fileName")
    }
}

private fun discoverState(): DiscoverUiState {
    val stories = (1..9).map { index ->
        DiscoverStoryItem(
            storyId = StoryId("shell-story-$index"),
            title = if (index == 1) "The Fox of the Moonlit Archive" else "Shell Story $index",
            coverUrl = null,
            contentType = ContentType.MANGA,
            score = Score(9.5 - index * 0.1, 10.0),
            genres = listOf("Fantasy", "Mystery"),
            publicationStatus = PublicationStatus.ONGOING,
            latestUpdate = CatalogLatestUpdate(2_000L - index, index.toString()),
        )
    }
    return DiscoverUiState(
        content = ContentState.Ready(
            DiscoverContent(
                selectedContentType = ContentType.MANGA,
                mediaTypeOptions = discoverMediaTypeOptions(),
                popular = stories.take(5),
                latestUpdates = stories.take(9),
                topRated = stories.take(5),
            ),
        ),
    )
}

private fun discoverEmptyState(): DiscoverUiState = DiscoverUiState(
    content = ContentState.Ready(
        DiscoverContent(
            selectedContentType = ContentType.MANGA,
            mediaTypeOptions = discoverMediaTypeOptions(),
            popular = emptyList(),
            latestUpdates = emptyList(),
            topRated = emptyList(),
            noContentReason = app.openstory.catalog.ui.discover.DiscoverNoContentReason.EMPTY_FEED,
        ),
    ),
)

private fun discoverMediaTypeOptions(): List<DiscoverMediaTypeOption> = listOf(
    DiscoverMediaTypeOption(ContentType.MANGA, enabled = true),
    DiscoverMediaTypeOption(ContentType.LIGHT_NOVEL, enabled = false),
)
