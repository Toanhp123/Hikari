package app.openstory.reader.ui

import android.content.res.Configuration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chromeVisibleDark() = capture(readerFixture(), "chrome-visible-dark.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chromeVisibleLight() = capture(readerFixture(), "chrome-visible-light.png", darkTheme = false)

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chromeHidden() = capture(readerFixture(), "chrome-hidden.png") {
        compose.onNodeWithTag("reader-content").performClick()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun settingsSheet() = capture(readerFixture(), "settings-sheet.png") {
        compose.onNodeWithContentDescription("Open reader settings").performClick()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun loading() = capture(ReaderUiState(loading = true), "loading.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun error() = capture(
        ReaderUiState(loading = false, failure = "The selected release is temporarily unavailable."),
        "error.png",
    )

    @Test @Config(sdk = [26], qualifiers = "w360dp-h800dp")
    fun api26TranslucentFallback() = capture(readerFixture(), "api-26-fallback.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun reduceMotion() = capture(readerFixture(), "reduce-motion.png", reduceMotion = true)

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun deviceFontScale200Percent() {
        val resources = RuntimeEnvironment.getApplication().resources
        val configuration = Configuration(resources.configuration).apply { fontScale = 2f }
        resources.updateConfiguration(configuration, resources.displayMetrics)
        capture(readerFixture(), "font-scale-200.png")
        compose.onNodeWithText("Mira opened the archive after midnight", substring = true).assertIsDisplayed()
    }

    private fun capture(
        state: ReaderUiState,
        fileName: String,
        darkTheme: Boolean = true,
        reduceMotion: Boolean = false,
        interaction: () -> Unit = {},
    ) {
        compose.setContent {
            HikariTheme(
                darkTheme = darkTheme,
                motionPolicy = HikariMotionPolicy(reduceMotion = reduceMotion),
            ) {
                ReaderScreen(state, ReaderActions())
            }
        }
        compose.waitForIdle()
        interaction()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/reader/$fileName")
    }
}

private fun readerFixture() = ReaderUiState(
    loading = false,
    chapterLabel = "Chapter 12 - The Locked Constellation",
    document = ReaderDocument(
        title = "The Fox of the Moonlit Archive",
        blocks = listOf(
            ReaderBlock.Heading("heading", 2, "The room behind the stars"),
            ReaderBlock.Paragraph(
                "paragraph-1",
                "Mira opened the archive after midnight, when every brass index reflected a different sky.",
            ),
            ReaderBlock.Paragraph(
                "paragraph-2",
                "A fox waited between the shelves. Its coat held the pale blue of a winter moon, and its shadow pointed toward a door that had not existed yesterday.",
            ),
            ReaderBlock.Note(
                "note",
                "Translator note: the original phrase can also mean a memory deliberately left unfinished.",
            ),
            ReaderBlock.Paragraph(
                "paragraph-3",
                "She followed without a lamp. The books whispered dates instead of titles, and each date belonged to a future no one had survived.",
            ),
        ),
        fingerprint = "reader-screenshot-fixture",
    ),
    releases = listOf(
        ReaderReleaseUiModel(ChapterReleaseId("release-a"), "Official", "MangaDex", "en"),
        ReaderReleaseUiModel(ChapterReleaseId("release-b"), "Community", "OpenStory", "vi"),
    ),
    selectedReleaseId = ChapterReleaseId("release-a"),
    previousChapterId = CanonicalChapterId("chapter-11"),
    nextChapterId = CanonicalChapterId("chapter-13"),
    availableOffline = true,
)
