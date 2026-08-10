package app.openstory.catalog.ui.story

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
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
            MaterialTheme {
                StoryScreen(
                    state = fixtureState(failed = true),
                    onRetry = {},
                    onSourceSelected = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Fixture Novel").assertIsDisplayed()
        compose.onNodeWithText("Source detail refresh failed: catalog.offline").assertIsDisplayed()
    }

    @Test
    fun retryAndSourceSelectionStayUiActions() {
        var retried = false
        var selected: Pair<PluginId, String>? = null
        compose.setContent {
            MaterialTheme {
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
    fun sourceSemanticsExposeCatalogMetadata() {
        compose.setContent {
            MaterialTheme {
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
