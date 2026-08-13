package app.openstory.catalog.ui.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import app.openstory.common.id.ChapterReleaseId
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
            HikariTheme { DownloadsScreen(downloadsFixture(), {}, { retried = it }, {}, {}, {}, {}) }
        }

        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onAllNodesWithText("Completed").assertCountEquals(2)
        compose.onAllNodesWithText("Failed").assertCountEquals(2)
        compose.onNodeWithContentDescription(
            "A Garden Made of Glass, Chapter 4, failed download",
        ).assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(ChapterReleaseId("failed"), retried)
    }
}

private fun downloadsFixture() = DownloadsUiState(
    active = listOf(downloadItem("active", DownloadState.RUNNING, "The Fox of the Moonlit Archive", "Chapter 12")),
    completed = listOf(downloadItem("complete", DownloadState.COMPLETED, "A Map of Quiet Stars", "Chapter 8")),
    failed = listOf(downloadItem("failed", DownloadState.FAILED, "A Garden Made of Glass", "Chapter 4", "network.timeout")),
    loading = false,
)

private fun downloadItem(id: String, state: DownloadState, title: String, chapter: String, failure: String? = null) =
    DownloadItemUiModel(
        ChapterReleaseId(id), app.openstory.common.id.StoryId("story-$id"), title, chapter,
        "content.fixture", state, 0L, failure, 10L,
    )
