package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class HomeDashboardSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun titleAndUtilityShareTheScrollableHeaderBand() {
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(fixture(), {}, {}, {}, {}, {}, onUtilityRequested = {})
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
        val baseContent = base.readyContent()
        val seed = baseContent.reading.single()
        val state = base.copy(
            content = ContentState.Ready(
                baseContent.copy(
                    paused = listOf(seed.copy(storyId = app.openstory.common.id.StoryId("story-paused"))),
                    completed = listOf(seed.copy(storyId = app.openstory.common.id.StoryId("story-completed"))),
                ),
            ),
        )
        lateinit var listState: LazyListState
        compose.setContent {
            listState = rememberLazyListState()
            HikariTheme {
                HomeDashboardScreen(
                    state, {}, {}, {}, {}, {},
                    onUtilityRequested = {},
                    listState = listState,
                )
            }
        }

        compose.onNodeWithTag("home-list").performScrollToIndex(6)
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun backToTopIsVisibleForAFirstItemOffset() {
        compose.setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemScrollOffset = 24)
            HikariTheme {
                HomeDashboardScreen(
                    fixture(), {}, {}, {}, {}, {},
                    onUtilityRequested = {},
                    listState = listState,
                )
            }
        }

        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed()
    }

    @Test
    fun shelfHeadingAndDirectionalFocusFollowVisualOrder() {
        val continueFocus = FocusRequester()
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(
                    fixture(), {}, {}, {}, {}, {}, firstContentFocusRequester = continueFocus,
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
                HomeDashboardScreen(fixture(), {}, {}, {}, {}, {}, onUtilityRequested = {})
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
                HomeDashboardScreen(fixture(), {}, {}, {}, {}, {})
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
                ContinueReadingCard(fixture().readyContent().continueReading.single(), {})
            }
        }

        compose.onNodeWithTag("story-poster-card", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun libraryPresentWithoutShelvesUsesTruthfulLocalEmptyCopy() {
        val contentFocus = FocusRequester()
        val content = fixture().readyContent().copy(
            summary = HomeReadingSummary(libraryCount = 1),
            continueReading = emptyList(),
            reading = emptyList(),
            planned = emptyList(),
            paused = emptyList(),
            completed = emptyList(),
            latestUpdates = emptyList(),
            noContentReason = HomeNoContentReason.LIBRARY_PRESENT_BUT_NO_HOME_SECTIONS,
        )
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(
                    HomeDashboardUiState(ContentState.Ready(content)),
                    {}, {}, {}, {}, {},
                    firstContentFocusRequester = contentFocus,
                )
            }
        }

        compose.onNodeWithText("No active reading shelves yet").assertIsDisplayed()
        compose.onNodeWithText("Discover stories").assertDoesNotExist()
        compose.runOnIdle { contentFocus.requestFocus() }
        compose.onNodeWithTag("home-local-empty").assertIsFocused()
    }

    @Test
    fun unknownDownloadCountRendersAnEmDash() {
        val content = fixture().readyContent().copy(
            summary = fixture().readyContent().summary.copy(downloadedCount = null),
        )
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(
                    HomeDashboardUiState(ContentState.Ready(content)),
                    {}, {}, {}, {}, {},
                )
            }
        }

        compose.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun observationIssueRetryUsesObservationCallback() {
        var retried = false
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(
                    state = fixture().copy(
                        observationIssue = CatalogUiFailure("home.catalog.observe_exception", true),
                    ),
                    onDiscover = {},
                    onStorySelected = {},
                    onResume = {},
                    onRetryContent = {},
                    onRetryObservation = { retried = true },
                )
            }
        }

        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun blockingFailureRetryUsesContentCallback() {
        var retried = false
        val contentFocus = FocusRequester()
        compose.setContent {
            HikariTheme {
                HomeDashboardScreen(
                    state = HomeDashboardUiState(
                        content = ContentState.Failed(
                            CatalogUiFailure("home.library.observe_exception", true),
                        ),
                    ),
                    onDiscover = {},
                    onStorySelected = {},
                    onResume = {},
                    onRetryContent = { retried = true },
                    onRetryObservation = {},
                    firstContentFocusRequester = contentFocus,
                )
            }
        }

        compose.runOnIdle { contentFocus.requestFocus() }
        compose.onNodeWithText("Retry").assertIsFocused()
        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }
}

private fun HomeDashboardUiState.readyContent(): HomeDashboardContent =
    (content as ContentState.Ready<HomeDashboardContent>).value
