package app.openstory.reader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import org.junit.Rule
import org.junit.Test

class ReaderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersStructuredTextWithAccessibleControlsAndSourceSwitcher() {
        compose.setContent {
            MaterialTheme {
                ReaderScreen(
                    state = ReaderUiState(
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
                            ReaderReleaseUiModel(ChapterReleaseId("a"), "A", "Source A", "en"),
                            ReaderReleaseUiModel(ChapterReleaseId("b"), "B", "Source B", "vi"),
                        ),
                        selectedReleaseId = ChapterReleaseId("a"),
                    ),
                    actions = ReaderActions(),
                )
            }
        }

        compose.onNodeWithText("Opening").assertIsDisplayed()
        compose.onNodeWithText("Readable paragraph").assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase reader text size").assertIsDisplayed()
        compose.onNodeWithText("Source A").performClick()
        compose.onNodeWithText("Source B · vi · B").assertIsDisplayed()
    }

    @Test
    fun changingDocumentReturnsTheReaderToTheNewDocumentStart() {
        lateinit var changeDocument: (ReaderDocument) -> Unit
        compose.setContent {
            var document by remember { mutableStateOf(longDocument("First")) }
            changeDocument = { document = it }
            MaterialTheme {
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

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("First paragraph 19"))
        compose.onNodeWithText("First paragraph 19").assertIsDisplayed()
        compose.runOnIdle { changeDocument(longDocument("Second")) }

        compose.onNodeWithText("Second title").assertIsDisplayed()
    }

    private fun longDocument(prefix: String) = ReaderDocument(
        "$prefix title",
        List(20) { index -> ReaderBlock.Paragraph("$prefix-$index", "$prefix paragraph $index") },
        "$prefix-fingerprint",
    )
}
