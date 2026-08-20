package app.openstory.catalog.ui.updates

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.openstory.catalog.ui.activity.LibraryActivityItem
import app.openstory.catalog.ui.components.ReaderTarget
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
class UpdatesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactDark() = capture(updatesFixture(), true, "compact-dark.png")

    @Test @Config(sdk = [35], qualifiers = "w600dp-h960dp")
    fun mediumLight() = capture(updatesFixture(), false, "medium-light.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun emptyDark() = capture(UpdatesUiState(loading = false), true, "empty-dark.png")

    private fun capture(state: UpdatesUiState, dark: Boolean, fileName: String) {
        compose.setContent {
            HikariTheme(darkTheme = dark, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                UpdatesScreen(state, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/updates/$fileName")
    }
}

internal fun updatesFixture(): UpdatesUiState {
    val storyId = StoryId("story-update")
    val target = ReaderTarget(storyId, CanonicalChapterId("chapter-update"), ChapterReleaseId("release-update"))
    return UpdatesUiState(
        groups = listOf(
            UpdatesGroupUiModel(
                "Aug 3, 2025",
                listOf(
                    LibraryActivityItem(
                        storyId, "The Fox of the Moonlit Archive", null, target.chapterId, target.releaseId,
                        "Chapter 12", "content.mangadex", "en", 1_754_236_800_000L, target,
                    ),
                ),
            ),
        ),
        loading = false,
    )
}
