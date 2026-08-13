package app.openstory.reader.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingReadingAreaHidesChromeWithoutRemovingContentAndRestoresIt() {
        setReaderContent()

        compose.onNodeWithTag("reader-top-chrome").assertIsDisplayed()
        compose.onNodeWithTag("reader-bottom-chrome").assertIsDisplayed()
        compose.onNodeWithTag("reader-content").performClick()

        compose.onNodeWithTag("reader-top-chrome").assertDoesNotExist()
        compose.onNodeWithTag("reader-bottom-chrome").assertDoesNotExist()
        compose.onNodeWithText("Readable paragraph").assertIsDisplayed()

        compose.onNodeWithTag("reader-content").performClick()
        compose.onNodeWithTag("reader-top-chrome").assertIsDisplayed()
        compose.onNodeWithTag("reader-bottom-chrome").assertIsDisplayed()
    }

    @Test
    fun chapterNavigationPreservesCanonicalChapterIdentities() {
        val previousId = CanonicalChapterId("canonical-previous")
        val nextId = CanonicalChapterId("canonical-next")
        var previous: CanonicalChapterId? = null
        var next: CanonicalChapterId? = null

        setReaderContent(
            state = readerState().copy(previousChapterId = previousId, nextChapterId = nextId),
            actions = ReaderActions(
                onPreviousChapter = { previous = it },
                onNextChapter = { next = it },
            ),
        )

        compose.onNodeWithText("Previous").performClick()
        compose.onNodeWithText("Next").performClick()
        assertEquals(previousId, previous)
        assertEquals(nextId, next)
    }

    @Test
    fun settingsHostsFontReleaseAndOfflineControlsWithoutChangingTheirIdentities() {
        val releaseId = ChapterReleaseId("release-b")
        var increased = false
        var selected: ChapterReleaseId? = null
        setReaderContent(
            state = readerState().copy(availableOffline = true),
            actions = ReaderActions(
                onIncreaseFont = { increased = true },
                onReleaseSelected = { selected = it },
            ),
        )

        compose.onNodeWithContentDescription("Open reader settings").performClick()
        compose.onNodeWithText("Reading settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase reader text size").performClick()
        assertTrue(increased)
        compose.onNodeWithText("Source A").performClick()
        compose.onNodeWithText("Source B - vi - B").performClick()
        assertEquals(releaseId, selected)
        compose.onNodeWithContentDescription("Chapter available offline").assertIsDisplayed()
    }

    @Test
    fun backAndDisposalFlushProgress() {
        var flushes = 0
        var backs = 0
        lateinit var disposeReader: () -> Unit
        compose.setContent {
            var showReader by remember { mutableStateOf(true) }
            disposeReader = { showReader = false }
            HikariTheme {
                if (showReader) {
                    ReaderScreen(
                        readerState(),
                        ReaderActions(onFlushProgress = { flushes++ }),
                        onBack = { backs++ },
                    )
                } else {
                    Text("Disposed")
                }
            }
        }

        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, flushes)
        assertEquals(1, backs)

        compose.runOnIdle(disposeReader)
        compose.waitForIdle()
        assertEquals(2, flushes)
    }

    @Test
    fun lifecycleStopFlushesProgress() {
        var flushes = 0
        val lifecycleOwner = TestLifecycleOwner()
        compose.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                HikariTheme {
                    ReaderScreen(readerState(), ReaderActions(onFlushProgress = { flushes++ }))
                }
            }
        }

        compose.runOnIdle { lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        assertEquals(1, flushes)
    }

    @Test
    fun changingDocumentReturnsTheReaderToTheNewDocumentStart() {
        lateinit var changeDocument: (ReaderDocument) -> Unit
        compose.setContent {
            var document by remember { mutableStateOf(longDocument("First")) }
            changeDocument = { document = it }
            HikariTheme {
                ReaderContent(
                    document = document,
                    fontScale = 1f,
                    restoredBlockId = null,
                    restoredCharacterOffset = 0,
                    contentPadding = PaddingValues(),
                    onPositionChanged = { _, _ -> },
                )
            }
        }

        compose.onNode(hasScrollAction()).performScrollToIndex(20)
        compose.onNodeWithText("First paragraph 19").assertIsDisplayed()
        compose.runOnIdle { changeDocument(longDocument("Second")) }

        compose.onNodeWithText("Second title").assertIsDisplayed()
    }

    @Test
    fun loadingUsesSharedReaderState() {
        compose.setContent {
            HikariTheme { ReaderScreen(ReaderUiState(loading = true), ReaderActions()) }
        }
        compose.onNodeWithText("Loading reader").assertIsDisplayed()
    }

    @Test
    fun unavailableReaderExposesWorkingRetry() {
        var retried = false
        compose.setContent {
            HikariTheme {
                ReaderScreen(
                    ReaderUiState(loading = false, failure = "Network unavailable"),
                    ReaderActions(onRetry = { retried = true }),
                )
            }
        }

        compose.onNodeWithText("Reader unavailable").assertIsDisplayed()
        compose.onNodeWithText("Network unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    private fun setReaderContent(
        state: ReaderUiState = readerState(),
        actions: ReaderActions = ReaderActions(),
        onBack: () -> Unit = {},
    ) {
        compose.setContent { HikariTheme { ReaderScreen(state, actions, onBack = onBack) } }
    }

    private fun readerState() = ReaderUiState(
        loading = false,
        chapterLabel = "Chapter 1",
        document = ReaderDocument(
            "Opening",
            listOf(
                ReaderBlock.Heading("heading", 2, "Arrival"),
                ReaderBlock.Paragraph("paragraph", "Readable paragraph"),
            ),
            "fingerprint",
        ),
        releases = listOf(
            ReaderReleaseUiModel(ChapterReleaseId("release-a"), "A", "Source A", "en"),
            ReaderReleaseUiModel(ChapterReleaseId("release-b"), "B", "Source B", "vi"),
        ),
        selectedReleaseId = ChapterReleaseId("release-a"),
    )

    private fun longDocument(prefix: String) = ReaderDocument(
        "$prefix title",
        List(20) { index -> ReaderBlock.Paragraph("$prefix-$index", "$prefix paragraph $index") },
        "$prefix-fingerprint",
    )
}

private class TestLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry
}
