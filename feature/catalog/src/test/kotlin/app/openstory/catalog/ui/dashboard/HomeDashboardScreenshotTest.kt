package app.openstory.catalog.ui.dashboard

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDashboardScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp") fun compactDark() = capture(fixture(), true, "compact-dark.png")
    @Test @Config(sdk = [35], qualifiers = "w412dp-h892dp") fun largePhoneDark() = capture(fixture(), true, "large-phone-dark.png")
    @Test @Config(sdk = [35], qualifiers = "w600dp-h960dp") fun mediumDark() = capture(fixture(), true, "medium-dark.png")
    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp") fun compactLight() = capture(fixture(), false, "compact-light.png")
    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp") fun initialLoading() = capture(HomeDashboardUiState(), true, "initial-loading.png")
    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp") fun trueEmpty() = capture(
        HomeDashboardUiState(
            content = ContentState.Ready(
                HomeDashboardContent(
                    summary = HomeReadingSummary(),
                    continueReading = emptyList(),
                    reading = emptyList(),
                    planned = emptyList(),
                    paused = emptyList(),
                    completed = emptyList(),
                    latestUpdates = emptyList(),
                    noContentReason = HomeNoContentReason.NO_LIBRARY,
                ),
            ),
        ),
        true,
        "true-empty.png",
    )

    private fun capture(state: HomeDashboardUiState, dark: Boolean, fileName: String) {
        compose.setContent {
            HikariTheme(darkTheme = dark, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                HomeDashboardScreen(state, {}, {}, {}, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/dashboard/$fileName")
    }
}

internal fun fixture(): HomeDashboardUiState {
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
        content = ContentState.Ready(
            HomeDashboardContent(
                summary = HomeReadingSummary(8, 3, 2, 4),
                continueReading = listOf(item),
                reading = listOf(item.copy(readerTarget = null)),
                planned = listOf(
                    item.copy(
                        storyId = StoryId("story-2"),
                        title = "A Map of Quiet Stars",
                        readerTarget = null,
                    ),
                ),
                paused = emptyList(),
                completed = emptyList(),
                latestUpdates = listOf(
                    HomeUpdateItem(
                        storyId,
                        item.title,
                        null,
                        target.chapterId,
                        target.releaseId,
                        "Chapter 12",
                        100L,
                        target,
                    ),
                ),
                noContentReason = null,
            ),
        ),
    )
}
