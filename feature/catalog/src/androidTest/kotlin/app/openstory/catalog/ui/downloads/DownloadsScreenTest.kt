package app.openstory.catalog.ui.downloads

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.downloads.DownloadState
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class DownloadsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sectionsExposeMetadataAndRetryUsesExactReleaseIdentity() {
        var retried: ChapterReleaseId? = null
        compose.setContent {
            HikariTheme {
                DownloadsScreen(
                    state = downloadsFixture(),
                    onStorySelected = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onRetry = { retried = it },
                    onCancel = {},
                    onRemove = {},
                    onConfirmRemoval = {},
                    onDismissRemoval = {},
                )
            }
        }

        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onAllNodesWithText("Completed").assertCountEquals(2)
        compose.onAllNodesWithText("Failed").assertCountEquals(2)
        compose.onNodeWithContentDescription(
            "A Garden Made of Glass, Chapter 4, failed download",
        ).assertIsDisplayed()
        compose.onNodeWithText("This download failed. Try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("network.timeout").assertDoesNotExist()
        compose.onNodeWithText("Retry").performScrollTo().performClick()

        assertEquals(ChapterReleaseId("failed"), retried)
    }

    @Test
    fun blockingFailureRendersContentRetry() {
        var retries = 0
        compose.setContent {
            HikariTheme {
                DownloadsScreen(
                    state = DownloadsUiState(
                        content = ContentState.Failed(
                            CatalogUiFailure("downloads.observe_failed", retryable = true),
                        ),
                    ),
                    onStorySelected = {},
                    onRetryContent = { retries += 1 },
                    onRetryObservation = {},
                    onRetry = {},
                    onCancel = {},
                    onRemove = {},
                    onConfirmRemoval = {},
                    onDismissRemoval = {},
                )
            }
        }

        compose.onNodeWithText("Downloads unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun readyObservationIssueKeepsContentAndUsesObservationRetry() {
        var retries = 0
        compose.setContent {
            HikariTheme {
                DownloadsScreen(
                    state = downloadsActiveOnlyFixture().copy(
                        observationIssue = CatalogUiFailure(
                            "downloads.chapters.observe_failed",
                            retryable = true,
                        ),
                    ),
                    onStorySelected = {},
                    onRetryContent = {},
                    onRetryObservation = { retries += 1 },
                    onRetry = {},
                    onCancel = {},
                    onRemove = {},
                    onConfirmRemoval = {},
                    onDismissRemoval = {},
                )
            }
        }

        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onNodeWithText("Couldn't update download details.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun readyEmptyObservationIssueRemainsVisible() {
        compose.setContent {
            HikariTheme {
                DownloadsScreen(
                    state = DownloadsUiState(
                        content = ContentState.Ready(DownloadsContent()),
                        observationIssue = CatalogUiFailure(
                            "downloads.catalog.observe_failed",
                            retryable = true,
                        ),
                    ),
                    onStorySelected = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onRetry = {},
                    onCancel = {},
                    onRemove = {},
                    onConfirmRemoval = {},
                    onDismissRemoval = {},
                )
            }
        }

        compose.onNodeWithText("No downloads yet").assertIsDisplayed()
        compose.onNodeWithText("Couldn't update download details.").assertIsDisplayed()
    }

    @Test
    fun readyCommandIssueIsVisibleWithoutObservationRetryAction() {
        compose.setContent {
            HikariTheme {
                DownloadsScreen(
                    state = downloadsFixture().copy(
                        commandFailure = CatalogUiFailure(
                            "downloads.command_failed",
                            retryable = false,
                        ),
                    ),
                    onStorySelected = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onRetry = {},
                    onCancel = {},
                    onRemove = {},
                    onConfirmRemoval = {},
                    onDismissRemoval = {},
                )
            }
        }

        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onNodeWithText("Couldn't update this download. Try the action again.").assertIsDisplayed()
    }
}

private fun downloadsActiveOnlyFixture() = DownloadsUiState(
    content = ContentState.Ready(
        DownloadsContent(
            active = listOf(
                downloadItem("active", DownloadState.RUNNING, "The Fox of the Moonlit Archive", "Chapter 12"),
            ),
        ),
    ),
)

private fun downloadsFixture() = DownloadsUiState(
    content = ContentState.Ready(
        DownloadsContent(
            active = listOf(
                downloadItem("active", DownloadState.RUNNING, "The Fox of the Moonlit Archive", "Chapter 12"),
            ),
            completed = listOf(
                downloadItem("complete", DownloadState.COMPLETED, "A Map of Quiet Stars", "Chapter 8"),
            ),
            failed = listOf(
                downloadItem("failed", DownloadState.FAILED, "A Garden Made of Glass", "Chapter 4", "network.timeout"),
            ),
        ),
    ),
)

private fun downloadItem(
    id: String,
    state: DownloadState,
    title: String,
    chapter: String,
    failure: String? = null,
) = DownloadItemUiModel(
    ChapterReleaseId(id),
    StoryId("story-$id"),
    title,
    chapter,
    "content.fixture",
    state,
    0L,
    failure,
    10L,
)
