package app.openstory.home.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.common.AppError
import app.openstory.home.model.HomeCatalog
import app.openstory.home.model.HomeCatalogCard
import app.openstory.home.model.HomeCatalogFreshness
import app.openstory.home.model.HomeCatalogSection
import app.openstory.home.model.HomeCombinedCard
import app.openstory.home.model.HomeCombinedSource
import app.openstory.home.model.HomeRefreshReport
import app.openstory.home.model.HomeSectionMembership
import app.openstory.home.model.HomeUiModel
import app.openstory.model.ContentType
import app.openstory.model.PluginId
import app.openstory.model.StoryId
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cachedContentAndPartialFailureRemainVisibleWhileRefreshing() {
        val state = fixtureState(refreshing = true, failed = true)

        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    state = state,
                    actions = HomeActions(
                        refresh = {},
                        storySelected = {},
                    ),
                )
            }
        }

        compose.onNodeWithText("Across catalogs").assertIsDisplayed()
        compose.onNodeWithText("Fixture Novel").assertIsDisplayed()
        compose.onNodeWithContentDescription("Refreshing catalog Home").assertIsDisplayed()
        compose.onNodeWithText("catalog.b refresh failed; cached content is still available.")
            .assertIsDisplayed()
    }

    @Test
    fun cardSemanticsExposeTitleSectionScoreAndContentType() {
        compose.setContent {
            MaterialTheme {
                CatalogHomeScreen(
                    catalog = fixtureState().home.catalogs.single(),
                    refreshing = false,
                    failure = null,
                    actions = HomeActions(
                        refresh = {},
                        storySelected = {},
                    ),
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Fixture Novel. Web novel. Section Trending. Score 8.4 out of 10 from catalog.a.",
        ).assertIsDisplayed()
    }

    @Test
    fun cardClickReportsCanonicalStoryId() {
        var selected: StoryId? = null

        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    state = fixtureState(),
                    actions = HomeActions(
                        refresh = {},
                        storySelected = { selected = it },
                    ),
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Fixture Novel. Web novel. Section Trending. Score 8.4 out of 10 from catalog.a.",
        ).performClick()
        assertEquals(StoryId("story-1"), selected)
    }

    @Test
    fun sourceSwitcherReportsSelectedCatalog() {
        var selected: PluginId? = null

        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    state = fixtureState(),
                    actions = HomeActions(
                        refresh = {},
                        storySelected = {},
                        catalogSelected = { selected = it },
                    ),
                )
            }
        }

        compose.onNodeWithText("catalog.a").performClick()
        assertEquals(PluginId("catalog.a"), selected)
    }
}

private fun fixtureState(
    refreshing: Boolean = false,
    failed: Boolean = false,
): HomeScreenState {
    val pluginId = PluginId("catalog.a")
    val storyId = StoryId("story-1")
    val section = fixtureSection(pluginId, storyId)
    val source = fixtureSource(pluginId)

    return HomeScreenState(
        home = HomeUiModel(
            combined = listOf(
                HomeCombinedCard(
                    storyId = storyId,
                    orderingScore = 0.84,
                    sources = listOf(source),
                ),
            ),
            catalogs = listOf(
                HomeCatalog(
                    pluginId = pluginId,
                    pluginVersion = "1.0.0",
                    refreshedAtEpochMillis = 100L,
                    sections = listOf(section),
                ),
            ),
        ),
        refreshing = refreshing,
        refreshReport = fixtureRefreshReport(failed),
    )
}

private fun fixtureSection(
    pluginId: PluginId,
    storyId: StoryId,
): HomeCatalogSection = HomeCatalogSection(
    sourceId = "trending",
    title = "Trending",
    items = listOf(
        HomeCatalogCard(
            storyId = storyId,
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            sourceId = "source-1",
            title = "Fixture Novel",
            contentType = ContentType.WEB_NOVEL,
            authors = setOf("Fixture Author"),
            coverReference = null,
            score = 8.4,
            scoreScale = 10.0,
            fetchedAtEpochMillis = 100L,
        ),
    ),
)

private fun fixtureSource(pluginId: PluginId): HomeCombinedSource = HomeCombinedSource(
    pluginId = pluginId,
    pluginVersion = "1.0.0",
    sourceId = "source-1",
    title = "Fixture Novel",
    contentType = ContentType.WEB_NOVEL,
    authors = setOf("Fixture Author"),
    coverReference = null,
    score = 8.4,
    scoreScale = 10.0,
    normalizedScore = 0.84,
    priorityWeight = 1.0,
    fetchedAtEpochMillis = 100L,
    sections = listOf(
        HomeSectionMembership(
            sourceId = "trending",
            title = "Trending",
            sectionPosition = 0,
            itemPosition = 0,
        ),
    ),
)

private fun fixtureRefreshReport(failed: Boolean): HomeRefreshReport? = if (failed) {
    HomeRefreshReport(
        failed = mapOf(
            PluginId("catalog.b") to AppError.Plugin(
                code = "catalog.refresh_failed",
                retryable = false,
            ),
        ),
        freshness = mapOf(
            PluginId("catalog.b") to HomeCatalogFreshness(
                refreshedAtEpochMillis = 50L,
                stale = true,
            ),
        ),
    )
} else {
    null
}
