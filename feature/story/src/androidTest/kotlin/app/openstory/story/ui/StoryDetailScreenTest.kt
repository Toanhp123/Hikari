package app.openstory.story.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.openstory.model.ContentType
import app.openstory.model.PluginId
import app.openstory.model.StoryId
import org.junit.Rule
import org.junit.Test

class StoryDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sourceSemanticsExposeTitleSourceScoreScaleAndFreshness() {
        composeRule.setContent {
            StoryDetailScreen(
                state = fixtureState(),
                onRetry = {},
            )
        }

        composeRule
            .onNodeWithContentDescription(
                "Fixture Novel, source catalog.a, Score 8.5 / 10.0, fetched 1234",
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
                pluginVersion = "1.0.0",
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
                publicationStatus = null,
                score = 8.5,
                scoreScale = 10.0,
                popularityRank = 3,
                fetchedAtEpochMillis = 1234L,
            ),
        ),
    ),
)
