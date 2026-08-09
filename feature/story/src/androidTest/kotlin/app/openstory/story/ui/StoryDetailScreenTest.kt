package app.openstory.story.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import org.junit.Rule
import org.junit.Test

class StoryDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sourceSemanticsExposeTitleSourceAndScoreScale() {
        composeRule.setContent {
            StoryDetailScreen(
                state = fixtureState(),
                onRetry = {},
            )
        }

        composeRule
            .onNodeWithContentDescription(
                "Fixture Novel, source catalog.a, Score 8.5 / 10.0",
            )
            .assertIsDisplayed()
    }
}

private fun fixtureState(): StoryDetailScreenState = StoryDetailScreenState(
    story = StoryDetailStory(
        storyId = StoryId("story-1"),
        preferredTitle = "Fixture Novel",
        contentType = ContentType.WEB_NOVEL,
        aliases = emptySet(),
        sources = listOf(
            StoryDetailSource(
                pluginId = PluginId("catalog.a"),
                sourceId = "source-a",
                sourceUrl = "https://example.com/source-a",
                title = "Fixture Novel",
                aliases = emptySet(),
                authors = setOf("Author"),
                description = "Description",
                genres = setOf("Fantasy"),
                contentType = ContentType.WEB_NOVEL,
                languageTags = setOf("en"),
                coverReference = null,
                score = 8.5,
                scoreScale = 10.0,
                popularityRank = 3,
            ),
        ),
    ),
)
