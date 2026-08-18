package app.openstory.catalog.ui.chapters

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
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
class ChapterListScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chapters() = capture(false, "chapters.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun offlineCachedChapters() = capture(true, "offline-cached-chapters.png")

    private fun capture(offline: Boolean, fileName: String) {
        val releaseId = ChapterReleaseId("release-12")
        val state = ChapterListUiState(
            storyId = StoryId("moonlit"), unreadCount = 2,
            chapters = listOf(
                ChapterItemUiModel(
                    CanonicalChapterId("chapter-12"), "Chapter 12 - The Locked Constellation", false, true,
                    listOf(ChapterReleaseUiModel(releaseId, PluginId("mangadex"), "MangaDex", "English", 1_786_560_000_000L, true)),
                ),
                ChapterItemUiModel(CanonicalChapterId("chapter-11"), "Chapter 11 - A Fox at Dawn", false, false, emptyList()),
            ),
        )
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                ChapterList(
                    state,
                    ChapterListActions(
                        downloadState = { if (offline) DownloadState.COMPLETED else null },
                        downloadActions = DownloadActions(),
                    ),
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/chapters/$fileName")
    }
}
