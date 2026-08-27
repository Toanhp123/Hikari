package app.openstory.catalog.ui.updates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.catalog.ui.activity.LibraryActivityItem
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdatesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun updateExposesSourceLanguageAndBothCanonicalNavigationActions() {
        var story: StoryId? = null
        var reader: ReaderTarget? = null
        compose.setContent {
            HikariTheme {
                UpdatesScreen(updatesFixture(), { story = it }, { reader = it }, {}, {})
            }
        }

        compose.onNodeWithText("Aug 3, 2025").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "The Fox of the Moonlit Archive, Chapter 12, content.mangadex, en",
        ).performClick()
        assertEquals(StoryId("story-update"), story)

        compose.onNodeWithText("Read").performClick()
        assertEquals("release-update", reader?.releaseId?.value)
    }

    @Test
    fun listOnlyUpdateNavigatesToStoryWithoutReadAction() {
        var story: StoryId? = null
        compose.setContent {
            HikariTheme {
                UpdatesScreen(updatesFixture(readerCapable = false), { story = it }, {}, {}, {})
            }
        }

        compose.onNodeWithText("Read").assertDoesNotExist()
        compose.onNodeWithContentDescription(
            "The Fox of the Moonlit Archive, Chapter 12, content.mangadex, en",
        ).performClick()
        assertEquals(StoryId("story-update"), story)
    }

    @Test
    fun blockingFailureExposesRetryAction() {
        var retries = 0
        compose.setContent {
            HikariTheme {
                UpdatesScreen(
                    state = UpdatesUiState(
                        content = ContentState.Failed(
                            CatalogUiFailure("updates.chapters.observe_failed", retryable = true),
                        ),
                    ),
                    onStorySelected = {},
                    onRead = {},
                    onRetryContent = { retries += 1 },
                    onRetryObservation = {},
                )
            }
        }

        compose.onNodeWithText("Updates unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun readyContentIssueRetriesObservationWithoutHidingContent() {
        var retries = 0
        compose.setContent {
            HikariTheme {
                UpdatesScreen(
                    state = updatesFixture(
                        observationIssue = CatalogUiFailure(
                            "updates.catalog.observe_failed",
                            retryable = true,
                        ),
                    ),
                    onStorySelected = {},
                    onRead = {},
                    onRetryContent = {},
                    onRetryObservation = { retries += 1 },
                )
            }
        }

        compose.onNodeWithText("The Fox of the Moonlit Archive").assertIsDisplayed()
        compose.onNodeWithText("Couldn't update all update details.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }
}

private fun updatesFixture(
    readerCapable: Boolean = true,
    observationIssue: CatalogUiFailure? = null,
): UpdatesUiState {
    val storyId = StoryId("story-update")
    val target = ReaderTarget(storyId, CanonicalChapterId("chapter-update"), ChapterReleaseId("release-update"))
    return UpdatesUiState(
        content = ContentState.Ready(
            UpdatesContent(
                groups = listOf(
                    UpdatesGroupUiModel(
                        "Aug 3, 2025",
                        listOf(
                            LibraryActivityItem(
                                storyId, "The Fox of the Moonlit Archive", null, target.chapterId, target.releaseId,
                                "Chapter 12", "content.mangadex", "en", 1_754_236_800_000L,
                                target.takeIf { readerCapable },
                            ),
                        ),
                    ),
                ),
            ),
        ),
        observationIssue = observationIssue,
    )
}
