package app.openstory.catalog.ui.story

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class StoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cachedStoryAndSourceFailureRenderTogether() {
        compose.setContent {
            HikariTheme {
                StoryScreen(
                    state = fixtureState(failed = true),
                    onRetry = {},
                    onSourceSelected = { _, _ -> },
                )
            }
        }

        compose.onAllNodesWithText("Fixture Novel").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Source detail refresh failed: catalog.offline").assertIsDisplayed()
    }

    @Test
    fun cachedStoryCanRequestDetailRefreshWithoutPriorFailure() {
        var refreshed = false
        compose.setContent {
            HikariTheme {
                StoryScreen(
                    state = fixtureState(),
                    onRetry = { refreshed = true },
                    onSourceSelected = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Refresh details").performClick()

        assertTrue(refreshed)
    }

    @Test
    fun retryAndSourceSelectionStayUiActions() {
        var retried = false
        var selected: Pair<PluginId, String>? = null
        compose.setContent {
            HikariTheme {
                StoryScreen(
                    state = fixtureState(failed = true),
                    onRetry = { retried = true },
                    onSourceSelected = { pluginId, sourceId -> selected = pluginId to sourceId },
                )
            }
        }

        compose.onNodeWithText("catalog.a").performClick()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(PluginId("catalog.a") to "source-a", selected)
        assertTrue(retried)
    }

    @Test
    fun noContentLoadingUsesSharedLoadingState() {
        compose.setContent {
            HikariTheme {
                StoryScreen(
                    state = fixtureState().copy(story = null, refreshing = true),
                    onRetry = {},
                    onSourceSelected = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Loading story").assertIsDisplayed()
    }

    @Test
    fun noContentRetryableFailureKeepsRetryAction() {
        var retried = false
        compose.setContent {
            HikariTheme {
                StoryScreen(
                    state = fixtureState(failed = true).copy(story = null),
                    onRetry = { retried = true },
                    onSourceSelected = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun sourceSemanticsExposeCatalogMetadata() {
        compose.setContent {
            HikariTheme {
                StoryScreen(
                    state = fixtureState(),
                    onRetry = {},
                    onSourceSelected = { _, _ -> },
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Fixture Novel, source catalog.a, Score unavailable",
        ).assertIsDisplayed()
    }
}

private fun fixtureState(failed: Boolean = false): StoryUiState {
    val storyId = StoryId("story-1")
    val entry = CatalogEntry(
        storyId = storyId,
        pluginId = PluginId("catalog.a"),
        sourceId = "source-a",
        title = "Fixture Novel",
        authors = setOf("Fixture Author"),
        contentType = ContentType.WEB_NOVEL,
    )
    return StoryUiState(
        storyId = storyId,
        story = StoryUiModel(
            storyId = storyId,
            preferredTitle = entry.title,
            contentType = entry.contentType,
            aliases = emptySet(),
            sources = listOf(entry),
        ),
        selectedSource = StorySourceIdentity(entry.pluginId, entry.sourceId),
        failure = if (failed) StoryRefreshFailure("catalog.offline", true) else null,
    )
}
