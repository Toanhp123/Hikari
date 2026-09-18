package app.universalmedia.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.RootId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryRootTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyLibraryExposesFolderPicker() {
        var added = 0
        compose.setContent {
            MaterialTheme { LibraryRoot(LibraryUiState(), { added++ }, {}, {}, {}) }
        }
        compose.onNodeWithText("Add Folder").performClick()
        assertEquals(1, added)
        compose.onNodeWithText("No videos in Library yet.").assertIsDisplayed()
    }

    @Test
    fun registrationInProgressDisablesDuplicateSubmission() {
        compose.setContent {
            MaterialTheme { LibraryRoot(LibraryUiState(isAddingRoot = true), {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Adding folder...").assertIsNotEnabled()
    }

    @Test
    fun scanningKeepsPersistedCardAndSelectionUsesCanonicalId() {
        val media = MediaId.generate()
        var selected: MediaId? = null
        val state = LibraryUiState(
            cards = listOf(LibraryCardUi(media, "movie.mp4")),
            roots = listOf(LibraryRootUi(RootId.generate(), LibraryScanStatus.RUNNING)),
        )
        compose.setContent { MaterialTheme { LibraryRoot(state, {}, { selected = it }, {}, {}) } }
        compose.onNodeWithText("Folder: Scan started; waiting for completion").assertIsDisplayed()
        compose.onNodeWithTag("media:${media.value}").performClick()
        assertEquals(media, selected)
        compose.onNodeWithText("movie.mp4").assertIsDisplayed()
    }

    @Test
    fun failedScanKeepsUntitledCardAndRetriesTheStableRoot() {
        val root = RootId.generate()
        var retried: RootId? = null
        val state = LibraryUiState(
            cards = listOf(LibraryCardUi(MediaId.generate(), null)),
            roots = listOf(LibraryRootUi(root, LibraryScanStatus.FAILED)),
            error = LibraryError.SCHEDULING,
        )
        compose.setContent { MaterialTheme { LibraryRoot(state, {}, {}, { retried = it }, {}) } }
        compose.onNodeWithText("Untitled video").assertIsDisplayed()
        compose.onNodeWithText("Could not schedule scan. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Scan Folder").performClick()
        assertEquals(root, retried)
    }

    @Test
    fun libraryReadFailureHasExplicitReloadAction() {
        var reloads = 0
        compose.setContent {
            MaterialTheme {
                LibraryRoot(LibraryUiState(error = LibraryError.LIBRARY), {}, {}, {}, { reloads++ })
            }
        }
        compose.onNodeWithText("Reload Library").performClick()
        assertEquals(1, reloads)
    }

    @Test
    fun registrationFailureCanReopenPicker() {
        var added = 0
        compose.setContent {
            MaterialTheme {
                LibraryRoot(LibraryUiState(error = LibraryError.REGISTRATION), {
                    added++
                }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Folder access failed. Choose the folder again.").assertIsDisplayed()
        compose.onNodeWithText("Add Folder").performClick()
        assertEquals(1, added)
    }
}
