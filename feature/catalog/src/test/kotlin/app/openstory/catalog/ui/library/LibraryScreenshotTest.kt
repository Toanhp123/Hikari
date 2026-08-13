package app.openstory.catalog.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.library.LibraryStatus
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactGridDark() = capture(fixture(), true, "compact-grid-dark.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactListLight() = capture(fixture().copy(displayMode = LibraryDisplayMode.LIST), false, "compact-list-light.png")

    @Test @Config(sdk = [35], qualifiers = "w412dp-h892dp")
    fun largePhoneGridDark() = capture(fixture(), true, "large-phone-grid-dark.png")

    @Test @Config(sdk = [35], qualifiers = "w600dp-h960dp")
    fun mediumGridDark() = capture(fixture(), true, "medium-grid-dark.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun filteredEmptyDark() = capture(fixture().copy(items = emptyList(), query = "missing"), true, "filtered-empty-dark.png")

    private fun capture(state: LibraryUiState, dark: Boolean, fileName: String) {
        compose.setContent {
            HikariTheme(darkTheme = dark, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                LibraryScreen(state, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/library/$fileName")
    }
}

private fun fixture(): LibraryUiState {
    val items = listOf(
        item("moon", "The Fox of the Moonlit Archive", LibraryStatus.READING, LibrarySourceState.LINKED, 0.64f),
        item("stars", "A Map of Quiet Stars", LibraryStatus.WANT_TO_READ, LibrarySourceState.NO_MAPPING, null),
        item("winter", "The Winter Index", LibraryStatus.PAUSED, LibrarySourceState.NO_MAPPING, 0.31f),
        item("glass", "A Garden Made of Glass", LibraryStatus.COMPLETED, LibrarySourceState.LINKED, 1f),
    )
    return LibraryUiState(
        items = items,
        totalCount = items.size,
        statusCounts = LibraryStatus.entries.associateWith { status -> items.count { it.status == status } },
        loading = false,
    )
}

private fun item(
    id: String,
    title: String,
    status: LibraryStatus,
    source: LibrarySourceState,
    progress: Float?,
) = LibraryItemUiModel(
    StoryId(id), title, ContentType.WEB_NOVEL, null, status, source, progress, 10L, 20L,
)
