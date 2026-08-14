package app.openstory.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.catalog.ui.discover.DiscoverQuickCategory
import app.openstory.catalog.ui.discover.DiscoverScreen
import app.openstory.catalog.ui.discover.DiscoverUiState
import app.openstory.catalog.ui.search.SearchScreen
import app.openstory.catalog.ui.search.SearchUiState
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.ui.HikariAppShell
import app.openstory.ui.HikariUtilitySheet
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

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
        val pluginId = PluginId("org.openstory.catalog.mangadex")
        compose.setContent {
            HikariTheme(darkTheme = false) {
                HikariAppShell(AppRoute.Discover, {}, {}) { contentPadding ->
                    DiscoverScreen(
                        state = DiscoverUiState(
                            featured = CatalogEntry(
                                storyId = StoryId("moonlit-archive"),
                                pluginId = pluginId,
                                sourceId = "moonlit-archive",
                                title = "The Fox of the Moonlit Archive",
                                genres = setOf("Fantasy", "Mystery"),
                                contentType = ContentType.LIGHT_NOVEL,
                                languageTags = setOf("en"),
                                score = Score(8.8, 10.0),
                            ),
                            quickCategories = listOf(
                                DiscoverQuickCategory(pluginId, "trending", "Trending stories"),
                            ),
                        ),
                        onRefresh = {},
                        onSearch = {},
                        onStorySelected = {},
                        onCatalogSelected = {},
                        onCombinedSelected = {},
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
                        state = SearchUiState(query = "moonlit archive"),
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
    fun shellAndDiscoverExposeDeterministicTraversalOrder() {
        val category = DiscoverQuickCategory(PluginId("catalog.a"), "trending", "Trending")
        compose.setContent {
            HikariTheme {
                HikariAppShell(AppRoute.Discover, {}, {}) { contentPadding ->
                    DiscoverScreen(
                        state = DiscoverUiState(quickCategories = listOf(category)),
                        onRefresh = {},
                        onSearch = {},
                        onStorySelected = {},
                        onCatalogSelected = {},
                        onCombinedSelected = {},
                        contentPadding = contentPadding,
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Search all stories").assertTraversalIndex(0f)
        compose.onNodeWithContentDescription("Open quick access").assertTraversalIndex(1f)
        compose.onNodeWithContentDescription("Category Trending from Catalog A").assertTraversalIndex(2f)
    }

    @Test
    fun discoverSearchAndUtilityShareTopBand() {
        compose.setContent {
            HikariTheme {
                HikariAppShell(AppRoute.Discover, {}, {}) { contentPadding ->
                    DiscoverScreen(
                        state = DiscoverUiState(),
                        onRefresh = {},
                        onSearch = {},
                        onStorySelected = {},
                        onCatalogSelected = {},
                        onCombinedSelected = {},
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
    fun keyboardFocusMovesAcrossDiscoverAndShellLayers() {
        val category = DiscoverQuickCategory(PluginId("catalog.a"), "trending", "Trending")
        val searchFocus = FocusRequester()
        val utilityFocus = FocusRequester()
        val categoryFocus = FocusRequester()
        val catalogFocus = FocusRequester()
        lateinit var inputModeManager: InputModeManager
        compose.setContent {
            HikariTheme {
                inputModeManager = LocalInputModeManager.current
                HikariAppShell(
                    currentRoute = AppRoute.Discover,
                    onTopLevelSelected = {},
                    onUtilityRequested = {},
                    utilityFocusRequester = utilityFocus,
                    utilityNextFocusRequester = categoryFocus,
                ) { contentPadding ->
                    DiscoverScreen(
                        state = DiscoverUiState(quickCategories = listOf(category)),
                        onRefresh = {},
                        onSearch = {},
                        onStorySelected = {},
                        onCatalogSelected = {},
                        onCombinedSelected = {},
                        searchFocusRequester = searchFocus,
                        searchNextFocusRequester = utilityFocus,
                        categoryFocusRequester = categoryFocus,
                        categoryNextFocusRequester = catalogFocus,
                        catalogFocusRequester = catalogFocus,
                        onUtilityRequested = {},
                        utilityFocusRequester = utilityFocus,
                        utilityNextFocusRequester = categoryFocus,
                        contentPadding = contentPadding,
                    )
                }
            }
        }

        val search = compose.onNodeWithContentDescription("Search all stories")
        val utility = compose.onNodeWithContentDescription("Open quick access")
        val quickCategory = compose.onNodeWithContentDescription("Category Trending from Catalog A")
        val combinedCatalog = compose.onNodeWithText("All sources")

        compose.runOnIdle {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            searchFocus.requestFocus()
        }
        search.assertIsFocused()
        search.performKeyInput { pressKey(Key.Tab) }
        utility.assertIsFocused()
        utility.performKeyInput { pressKey(Key.Tab) }
        quickCategory.assertIsFocused()
        quickCategory.performKeyInput { pressKey(Key.Tab) }
        combinedCatalog.assertIsFocused()
    }

    @Test
    fun keyboardFocusFallsBackToCatalogWhenCategoriesAreEmpty() {
        val searchFocus = FocusRequester()
        val utilityFocus = FocusRequester()
        val categoryFocus = FocusRequester()
        val catalogFocus = FocusRequester()
        lateinit var inputModeManager: InputModeManager
        compose.setContent {
            HikariTheme {
                inputModeManager = LocalInputModeManager.current
                HikariAppShell(
                    currentRoute = AppRoute.Home,
                    onTopLevelSelected = {},
                    onUtilityRequested = {},
                    utilityFocusRequester = utilityFocus,
                    utilityNextFocusRequester = categoryFocus,
                ) { contentPadding ->
                    DiscoverScreen(
                        state = DiscoverUiState(),
                        onRefresh = {},
                        onSearch = {},
                        onStorySelected = {},
                        onCatalogSelected = {},
                        onCombinedSelected = {},
                        searchFocusRequester = searchFocus,
                        searchNextFocusRequester = utilityFocus,
                        categoryFocusRequester = categoryFocus,
                        categoryNextFocusRequester = catalogFocus,
                        catalogFocusRequester = catalogFocus,
                        onUtilityRequested = {},
                        utilityFocusRequester = utilityFocus,
                        utilityNextFocusRequester = catalogFocus,
                        contentPadding = contentPadding,
                    )
                }
            }
        }

        val search = compose.onNodeWithContentDescription("Search all stories")
        val utility = compose.onNodeWithContentDescription("Open quick access")
        val combinedCatalog = compose.onNodeWithText("All sources")

        compose.runOnIdle {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            searchFocus.requestFocus()
        }
        search.assertIsFocused()
        search.performKeyInput { pressKey(Key.Tab) }
        utility.assertIsFocused()
        utility.performKeyInput { pressKey(Key.Tab) }
        combinedCatalog.assertIsFocused()
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

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTraversalIndex(index: Float) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, index))
