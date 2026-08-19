package app.openstory.catalog.ui.dashboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Rule
import org.junit.Test

class HomeDashboardScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun headingsAndCardsExposeTalkBackSemantics() {
        compose.setContent { HikariTheme { HomeDashboardScreen(fixture(), {}, {}, {}) } }

        compose.onNodeWithText("Continue Reading", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Resume The Fox of the Moonlit Archive, Chapter 12, 64 percent read",
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "The Fox of the Moonlit Archive. Section Reading",
        ).assertIsDisplayed()
    }

    @Test
    fun dPadFocusMovesInVisualOrder() {
        lateinit var inputModeManager: InputModeManager
        compose.setContent {
            HikariTheme {
                inputModeManager = LocalInputModeManager.current
                HomeDashboardScreen(fixture(), {}, {}, {})
            }
        }
        val continueCard = compose.onNodeWithContentDescription(
            "Resume The Fox of the Moonlit Archive, Chapter 12, 64 percent read",
        )
        val readingCard = compose.onNodeWithContentDescription(
            "The Fox of the Moonlit Archive. Section Reading",
        )

        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        continueCard.performSemanticsAction(SemanticsActions.RequestFocus)
        continueCard.assertIsFocused()
        continueCard.performKeyInput { pressKey(Key.DirectionDown) }
        readingCard.assertIsFocused()
    }

    @Test
    fun listOnlyLatestUpdateOpensStoryInsteadOfReader() {
        val storyId = StoryId("story-list-only")
        var openedStory: StoryId? = null
        var openedReader: ReaderTarget? = null
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(
                    state = HomeDashboardUiState(
                        latestUpdates = listOf(
                            HomeUpdateItem(
                                storyId = storyId,
                                title = "List-only story",
                                coverUrl = null,
                                chapterId = CanonicalChapterId("chapter-1"),
                                releaseId = ChapterReleaseId("release-1"),
                                chapterLabel = "Chapter 1",
                                publishedAtEpochMillis = 1L,
                                readerTarget = null,
                            ),
                        ),
                        loading = false,
                    ),
                    onDiscover = {},
                    onStorySelected = { openedStory = it },
                    onResume = { openedReader = it },
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Open List-only story, Chapter 1. Section Latest Updates",
        ).performClick()

        kotlin.test.assertEquals(storyId, openedStory)
        kotlin.test.assertEquals(null, openedReader)
    }

    @Test
    fun emptyFallbackStillExposesObservationFailure() {
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(
                    HomeDashboardUiState(
                        loading = false,
                        failure = HomeDashboardFailure("home.catalog.observe_exception", true),
                    ),
                    {}, {}, {},
                )
            }
        }

        compose.onNodeWithText("Some reading data could not be refreshed.").assertIsDisplayed()
        compose.onNodeWithText("home.catalog.observe_exception").assertDoesNotExist()
    }
}

private fun fixture(): HomeDashboardUiState {
    val storyId = StoryId("story-1")
    val target = ReaderTarget(storyId, CanonicalChapterId("chapter-12"), ChapterReleaseId("release-12"))
    val item = HomeDashboardItem(
        storyId = storyId,
        title = "The Fox of the Moonlit Archive",
        coverUrl = null,
        readerTarget = target,
        progressFraction = 0.64f,
        chapterLabel = "Chapter 12",
        lastActivityAtEpochMillis = 100L,
    )
    return HomeDashboardUiState(
        summary = HomeReadingSummary(8, 3, 2, 4),
        continueReading = listOf(item),
        reading = listOf(item.copy(readerTarget = null)),
        loading = false,
    )
}
