package app.openstory.catalog.ui.downloads

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.downloads.DownloadState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactDark() = capture(downloadsFixture(), true, "compact-dark.png")

    @Test @Config(sdk = [35], qualifiers = "w600dp-h960dp")
    fun mediumLight() = capture(downloadsFixture(), false, "medium-light.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun emptyDark() = capture(DownloadsUiState(loading = false), true, "empty-dark.png")

    private fun capture(state: DownloadsUiState, dark: Boolean, fileName: String) {
        compose.setContent {
            HikariTheme(darkTheme = dark, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                DownloadsScreen(state, {}, {}, {}, {}, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/downloads/$fileName")
    }
}

internal fun downloadsFixture() = DownloadsUiState(
    active = listOf(downloadItem("active", DownloadState.RUNNING, "The Fox of the Moonlit Archive", "Chapter 12")),
    completed = listOf(downloadItem("complete", DownloadState.COMPLETED, "A Map of Quiet Stars", "Chapter 8", 2_048L)),
    failed = listOf(downloadItem("failed", DownloadState.FAILED, "A Garden Made of Glass", "Chapter 4", failure = "network.timeout")),
    loading = false,
)

private fun downloadItem(
    id: String,
    state: DownloadState,
    title: String,
    chapter: String,
    size: Long = 0L,
    failure: String? = null,
) = DownloadItemUiModel(
    ChapterReleaseId(id), StoryId("story-$id"), title, chapter, "content.fixture", state, size, failure, 10L,
)
