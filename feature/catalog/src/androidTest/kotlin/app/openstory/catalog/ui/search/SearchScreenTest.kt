package app.openstory.catalog.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.search.CatalogSearchFailure
import app.openstory.catalog.search.CatalogSearchFilterGroup
import app.openstory.catalog.search.CatalogSearchSourceCard
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.catalog.source.SourceFilterOption
import app.openstory.catalog.source.SourceOptionFilter
import app.openstory.catalog.source.SourceTextFilter
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun successfulResultsRemainVisibleBesideSourceFailures() {
        compose.setContent {
            HikariTheme {
                SearchScreen(
                    state = fixtureState(failed = true),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { _, _, _ -> },
                    onClearFilters = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithText("Fixture Novel").assertIsDisplayed()
        compose.onNodeWithText("catalog.offline").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun resultClickReportsCanonicalStoryIdentityOnly() {
        var selected: CatalogSearchStory? = null
        compose.setContent {
            HikariTheme {
                SearchScreen(
                    state = fixtureState(),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { _, _, _ -> },
                    onClearFilters = {},
                    onStorySelected = { selected = it },
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Fixture Novel. Web novel. catalog.a score 8.4 out of 10.",
        ).performClick()
        assertEquals(StoryId("story-1"), selected?.story?.id)
    }

    @Test
    fun filterSelectionKeepsSourceIdentity() {
        val selections = mutableListOf<Triple<PluginId, String, List<String>>>()
        compose.setContent {
            HikariTheme {
                SearchScreen(
                    state = fixtureState(),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { pluginId, filterId, values ->
                        selections += Triple(pluginId, filterId, values)
                    },
                    onClearFilters = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithText("MangaDex").performClick()
        compose.onNodeWithText("Manga").performClick()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Fantasy").performClick()
        assertEquals(
            listOf(
                Triple(PluginId("catalog.a"), "source", listOf("mangadex")),
                Triple(PluginId("catalog.a"), "content-type", listOf("manga")),
                Triple(PluginId("catalog.a"), "language", listOf("en")),
                Triple(PluginId("catalog.a"), "genre", listOf("fantasy")),
            ),
            selections,
        )
    }

    @Test
    fun searchFieldExposesKeyboardSubmission() {
        compose.setContent {
            HikariTheme {
                SearchScreen(
                    state = fixtureState(),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { _, _, _ -> },
                    onClearFilters = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNode(hasImeAction(ImeAction.Search)).performImeAction()
        compose.onNodeWithText("Fixture Novel").assertIsDisplayed()
    }

    @Test
    fun longRuntimeFilterContentCanScrollToResults() {
        val pluginId = PluginId("catalog.long")
        val state = fixtureState().copy(
            filterGroups = listOf(
                CatalogSearchFilterGroup(
                    pluginId,
                    (1..8).map { index -> SourceTextFilter("filter-$index", "Runtime filter $index") },
                ),
            ),
        )
        compose.setContent {
            HikariTheme {
                SearchScreen(state, {}, {}, { _, _, _ -> }, {}, {})
            }
        }

        val content = compose.onNodeWithTag("search-content")
        content.performScrollToKey("search-filter-catalog.long-filter-8")
        compose.onNodeWithText("Runtime filter 8").assertIsDisplayed()
        content.performScrollToKey("story-1")
        compose.onNodeWithText("Fixture Novel").assertIsDisplayed()
    }

    @Test
    fun completedSearchWithoutResultsShowsDistinctEmptyState() {
        compose.setContent {
            HikariTheme {
                SearchScreen(
                    state = SearchUiState(query = "missing title"),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { _, _, _ -> },
                    onClearFilters = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithText("No matches found").assertIsDisplayed()
        compose.onNodeWithText("Try another title, author, or alias.").assertIsDisplayed()
    }

    @Test
    fun focusedHeaderRespectsShellPaddingAndNavigatesBack() {
        var backRequested = false
        compose.setContent {
            HikariTheme {
                SearchScreen(
                    state = fixtureState(),
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

        val back = compose.onNodeWithContentDescription("Back")
        val bounds = back.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= 40f, "Focused header must begin below shell-provided top padding")
        back.performClick()
        assertTrue(backRequested)
    }
}

private fun fixtureState(failed: Boolean = false): SearchUiState {
    val pluginId = PluginId("catalog.a")
    return SearchUiState(
        query = "fixture",
        filterGroups = listOf(
            CatalogSearchFilterGroup(
                pluginId,
                listOf(
                    SourceOptionFilter(
                        id = "source",
                        label = "Source",
                        multiple = false,
                        options = listOf(SourceFilterOption("mangadex", "MangaDex")),
                    ),
                    SourceOptionFilter(
                        id = "content-type",
                        label = "Content type",
                        multiple = false,
                        options = listOf(SourceFilterOption("manga", "Manga")),
                    ),
                    SourceOptionFilter(
                        id = "language",
                        label = "Language",
                        multiple = true,
                        options = listOf(SourceFilterOption("en", "English")),
                    ),
                    SourceOptionFilter(
                        id = "genre",
                        label = "Genre",
                        multiple = true,
                        options = listOf(SourceFilterOption("fantasy", "Fantasy")),
                    ),
                ),
            ),
        ),
        stories = listOf(
            CatalogSearchStory(
                story = Story(StoryId("story-1"), ContentType.WEB_NOVEL),
                sources = listOf(
                    CatalogSearchSourceCard(
                        pluginId = pluginId,
                        sourceId = "source-1",
                        title = "Fixture Novel",
                        contentType = ContentType.WEB_NOVEL,
                        authors = setOf("Fixture Author"),
                        coverUrl = null,
                        score = Score(8.4, 10.0),
                    ),
                ),
            ),
        ),
        failures = if (failed) {
            listOf(CatalogSearchFailure(PluginId("catalog.b"), "catalog.offline", true))
        } else {
            emptyList()
        },
    )
}
