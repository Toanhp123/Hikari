package app.openstory.catalog.ui.discover

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class DiscoverSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun flexibleHeaderPrioritizesSearchBesideUtilityAction() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                    onUtilityRequested = {},
                )
            }
        }

        val searchBounds = compose.onNodeWithContentDescription("Search all stories")
            .fetchSemanticsNode().boundsInRoot
        val utilityBounds = compose.onNodeWithContentDescription("Open quick access")
            .fetchSemanticsNode().boundsInRoot

        compose.onNodeWithText("Discover").assertDoesNotExist()
        assertTrue(searchBounds.top < utilityBounds.bottom && utilityBounds.top < searchBounds.bottom)
    }

    @Test
    fun pullRefreshReplacesManualRefreshAction() {
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(),
                    onRefresh = { refreshCalls += 1 },
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        val refreshAction = compose.onNodeWithTag("discover-pull-refresh")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Refresh" }

        compose.runOnIdle {
            assertTrue(refreshAction.action())
            assertEquals(1, refreshCalls)
        }
        compose.onNodeWithText("Refresh sources").assertDoesNotExist()
    }

    @Test
    fun partialRefreshFailureKeepsVisibleRetryAction() {
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(
                        refreshReport = DiscoverRefreshReport(
                            failed = mapOf(
                                PluginId("catalog.a") to "catalog.offline",
                                PluginId("catalog.b") to "catalog.timeout",
                            ),
                        ),
                    ),
                    onRefresh = { refreshCalls += 1 },
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        compose.onAllNodesWithText("Retry").assertCountEquals(1)
        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, refreshCalls)
    }

    @Test
    fun retryableGlobalFailureKeepsVisibleRetryAction() {
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(
                        refreshFailure = DiscoverUiFailure("catalog.offline", retryable = true),
                    ),
                    onRefresh = { refreshCalls += 1 },
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, refreshCalls)
    }

    @Test
    fun featuredSemanticsExposeTitleScoreAndSource() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(featured = fixtureEntry()),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Featured Fixture Novel. Score 8.4 out of 10 from Catalog A.",
        ).assertIsDisplayed()
    }

    @Test
    fun catalogSelectorUsesFriendlyPluginNameInsteadOfRawId() {
        val pluginId = PluginId("org.openstory.catalog.mangadex")
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(
                        catalogs = listOf(
                            app.openstory.catalog.model.CatalogHomeSnapshot(
                                pluginId = pluginId,
                                pluginVersion = "1",
                                refreshedAtEpochMillis = 1L,
                                sections = emptyList(),
                            ),
                        ),
                    ),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        compose.onNodeWithText("MangaDex").assertIsDisplayed()
        compose.onNodeWithText(pluginId.value).assertDoesNotExist()
    }

    @Test
    fun searchTargetAndShelfHeadingRemainAccessible() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(
                        shelves = listOf(
                            DiscoverShelf(null, "ranked", "Across catalogs", listOf(fixtureEntry())),
                        ),
                    ),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Search all stories").assertHeightIsAtLeast(48.dp)
        compose.onAllNodesWithText("Across catalogs", useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
        compose.onNodeWithTag("story-poster-card", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun executableCategoryTargetIsAtLeastFortyEightDp() {
        var selected: DiscoverQuickCategory? = null
        val category = DiscoverQuickCategory(PluginId("catalog.a"), "trending", "Trending")
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(
                        quickCategories = listOf(category),
                    ),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                    onCategorySelected = { selected = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Category Trending from Catalog A")
            .assertHeightIsAtLeast(56.dp)
            .performClick()
        kotlin.test.assertEquals(category, selected)
    }

    @Test
    fun selectedCategoryExposesSelectedSemantics() {
        val category = DiscoverQuickCategory(PluginId("catalog.a"), "trending", "Trending")
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(
                        quickCategories = listOf(category),
                        selectedCatalogId = category.pluginId,
                        selectedSourceId = category.sourceId,
                    ),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Category Trending from Catalog A")
            .assertIsSelected()
    }

    @Test
    fun keyboardTraversalMovesFromSearchToCategoryToCatalog() {
        val category = DiscoverQuickCategory(PluginId("catalog.a"), "trending", "Trending")
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(
                        quickCategories = listOf(category),
                        catalogs = app.openstory.catalog.model.CatalogHomeSnapshot(
                            pluginId = category.pluginId,
                            pluginVersion = "1",
                            refreshedAtEpochMillis = 1L,
                            sections = emptyList(),
                        ).let(::listOf),
                    ),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        val search = compose.onNodeWithContentDescription("Search all stories")
        search.performKeyInput { pressKey(Key.Tab) }
        search.assertIsFocused()
        search.performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithContentDescription("Open quick access").assertIsFocused()
        compose.onNodeWithContentDescription("Open quick access")
            .performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithContentDescription("Category Trending from Catalog A").assertIsFocused()
        compose.onNodeWithContentDescription("Category Trending from Catalog A")
            .performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithText("All sources").assertIsFocused()
    }
}

private fun fixtureEntry() = CatalogEntry(
    storyId = StoryId("story-1"),
    pluginId = PluginId("catalog.a"),
    sourceId = "source-1",
    title = "Fixture Novel",
    contentType = ContentType.WEB_NOVEL,
    score = Score(8.4, 10.0),
)
