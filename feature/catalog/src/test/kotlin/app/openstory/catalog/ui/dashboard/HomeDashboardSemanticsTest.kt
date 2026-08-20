package app.openstory.catalog.ui.dashboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onNodeWithText
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class HomeDashboardSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun titleAndUtilityShareTheScrollableHeaderBand() {
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(fixture(), {}, {}, {}, onUtilityRequested = {})
            }
        }

        val titleBounds = compose.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot
        val utilityBounds = compose.onNodeWithContentDescription("Open quick access")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(titleBounds.top < utilityBounds.bottom && utilityBounds.top < titleBounds.bottom)
    }

    @Test
    fun topLevelHeaderStaysPinnedAndBackToTopReturnsToTheStart() {
        val base = fixture()
        val seed = base.reading.single()
        val state = base.copy(
            paused = listOf(seed.copy(storyId = app.openstory.common.id.StoryId("story-paused"))),
            completed = listOf(seed.copy(storyId = app.openstory.common.id.StoryId("story-completed"))),
        )
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(state, {}, {}, {}, onUtilityRequested = {})
            }
        }

        compose.onNodeWithTag("home-list").performScrollToIndex(6)
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back to top").assertDoesNotExist()
    }

    @Test
    fun shelfHeadingAndDirectionalFocusFollowVisualOrder() {
        val continueFocus = FocusRequester()
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(
                    fixture(), {}, {}, {}, firstContentFocusRequester = continueFocus,
                )
            }
        }

        compose.onNodeWithText("Continue Reading", useUnmergedTree = true)
            .assertIsDisplayed()
        val continueCard = compose.onNodeWithContentDescription(
            "Resume The Fox of the Moonlit Archive, Chapter 12, 64 percent read",
        )
        val readingCard = compose.onNodeWithContentDescription(
            "The Fox of the Moonlit Archive. Section Reading",
        )
        compose.runOnIdle { continueFocus.requestFocus() }
        continueCard.assertIsFocused()
        continueCard.performKeyInput { pressKey(Key.DirectionDown) }
        readingCard.assertIsFocused()
    }

    @Test
    fun atmosphereExtendsBehindTheTopLevelHeader() {
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(fixture(), {}, {}, {}, onUtilityRequested = {})
            }
        }

        val atmosphere = compose.onNodeWithTag("home-atmosphere")
            .fetchSemanticsNode().boundsInRoot
        val utility = compose.onNodeWithContentDescription("Open quick access")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(atmosphere.top <= 1f)
        assertTrue(atmosphere.bottom >= utility.bottom)
    }

    @Test
    fun readingShelvesExposeTheSharedPosterCard() {
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(fixture(), {}, {}, {})
            }
        }

        compose.onAllNodesWithTag("story-poster-card", useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun continueReadingUsesTheSharedPosterCard() {
        compose.setContent {
            HikariTheme {
                ContinueReadingCard(fixture().continueReading.single(), {})
            }
        }

        compose.onNodeWithTag("story-poster-card", useUnmergedTree = true).assertIsDisplayed()
    }
}
