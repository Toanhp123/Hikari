package app.openstory.catalog.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
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
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cachedContentAndPartialFailureRemainVisibleWhileRefreshing() {
        compose.setContent {
            HikariTheme {
                HomeScreen(
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
        compose.onNodeWithText("Fixture Novel").assertIsDisplayed()
        compose.onNodeWithContentDescription("Refreshing catalog Home").assertIsDisplayed()
        compose.onNodeWithText("catalog.b refresh failed; cached content is still available.")
            .assertIsDisplayed()
    }

    @Test
    fun cardSemanticsExposeStorySectionScoreAndContentType() {
        compose.setContent {
            HikariTheme {
                HomeScreen(
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
                HomeScreen(
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
): HomeUiState {
    val entry = fixtureEntry()
    val pluginId = entry.pluginId
    return HomeUiState(
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
            HomeRefreshReport(failed = mapOf(PluginId("catalog.b") to "catalog.offline"))
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
