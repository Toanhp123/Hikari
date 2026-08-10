package app.openstory.catalog.ui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.search.CatalogSearchFailure
import app.openstory.catalog.search.CatalogSearchFilterGroup
import app.openstory.catalog.search.CatalogSearchSourceCard
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.catalog.source.SourceFilterOption
import app.openstory.catalog.source.SourceOptionFilter
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun successfulResultsRemainVisibleBesideSourceFailures() {
        compose.setContent {
            MaterialTheme {
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
        compose.onNodeWithText("catalog.b: catalog.offline").assertIsDisplayed()
    }

    @Test
    fun resultClickReportsCanonicalStoryIdentityOnly() {
        var selected: StoryId? = null
        compose.setContent {
            MaterialTheme {
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
        assertEquals(StoryId("story-1"), selected)
    }

    @Test
    fun filterSelectionKeepsSourceIdentity() {
        var selection: Triple<PluginId, String, List<String>>? = null
        compose.setContent {
            MaterialTheme {
                SearchScreen(
                    state = fixtureState(),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { pluginId, filterId, values ->
                        selection = Triple(pluginId, filterId, values)
                    },
                    onClearFilters = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithText("Fantasy").performClick()
        assertEquals(
            Triple(PluginId("catalog.a"), "genre", listOf("fantasy")),
            selection,
        )
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
