package app.openstory.catalog.ui.discover

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.ranking.CatalogRankContribution
import app.openstory.catalog.ranking.RankedCatalogStory
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class DiscoverScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cachedContentAndPartialFailureRemainVisibleWhileRefreshing() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = fixtureState(refreshing = true, failed = true),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        compose.onAllNodesWithText("Across catalogs").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Fixture Novel").onFirst().assertIsDisplayed()
        compose.onNodeWithTag("discover-pull-refresh").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Refreshing"),
        )
        compose.onNodeWithText("Catalog B refresh failed; cached content is still available.")
            .assertIsDisplayed()
    }

    @Test
    fun pullRefreshReplacesManualDiscoverRefreshAction() {
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = fixtureState(),
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
        assertTrue(refreshAction.action())
        assertEquals(1, refreshCalls)
        compose.onAllNodesWithText("Refresh sources").assertCountEquals(0)
    }

    @Test
    fun cardSemanticsExposeStorySectionScoreAndContentType() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = fixtureState(selected = true),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = {},
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Fixture Novel. Web novel. Section Trending. Score 8.4 out of 10 from catalog.a.",
        ).assertIsDisplayed()
    }

    @Test
    fun cardClickReportsCanonicalStoryIdentityOnly() {
        var selected: StoryId? = null
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = fixtureState(),
                    onRefresh = {},
                    onSearch = {},
                    onStorySelected = { selected = it },
                    onCatalogSelected = {},
                    onCombinedSelected = {},
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Fixture Novel. Web novel. Section Across catalogs. Score 8.4 out of 10 from catalog.a.",
        ).performClick()
        assertEquals(StoryId("story-1"), selected)
    }
}

private fun fixtureState(
    refreshing: Boolean = false,
    failed: Boolean = false,
    selected: Boolean = false,
): DiscoverUiState {
    val entry = fixtureEntry()
    val pluginId = entry.pluginId
    return projectDiscoverState(
        catalogs = listOf(
            CatalogHomeSnapshot(
                pluginId,
                "1.0.0",
                100L,
                listOf(CatalogHomeSection("trending", "Trending", listOf(entry))),
            ),
        ),
        rankedStories = listOf(
            RankedCatalogStory(
                entry.storyId,
                0.84,
                listOf(CatalogRankContribution(entry, 0.84, 1.0)),
            ),
        ),
        selectedCatalogId = pluginId.takeIf { selected },
        refreshing = refreshing,
        refreshReport = if (failed) {
            DiscoverRefreshReport(failed = mapOf(PluginId("catalog.b") to "catalog.offline"))
        } else {
            null
        },
    )
}

private fun fixtureEntry() = CatalogEntry(
    storyId = StoryId("story-1"),
    pluginId = PluginId("catalog.a"),
    sourceId = "source-1",
    title = "Fixture Novel",
    authors = setOf("Fixture Author"),
    contentType = ContentType.WEB_NOVEL,
    score = Score(8.4, 10.0),
)
