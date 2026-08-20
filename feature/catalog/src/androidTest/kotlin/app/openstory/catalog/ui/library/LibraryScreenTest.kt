package app.openstory.catalog.ui.library

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.library.LibraryStatus
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun libraryUsesSharedAtmosphereBackground() {
        show(fixtureState())

        compose.onNodeWithTag("library-atmosphere").assertIsDisplayed()
    }

    @Test
    fun filterActionOpensCurrentFilterControls() {
        show(fixtureState())

        compose.onNodeWithContentDescription("Open Library filters").performClick()
        compose.onNodeWithText("Library filters").assertIsDisplayed()
        compose.onNodeWithTag("library-status-all").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("library-source-all").assertIsDisplayed()
        compose.onNodeWithTag("library-sort-last_activity").assertIsDisplayed()
    }

    @Test
    fun storyCardExposesStatusProgressAndSourceWithoutColor() {
        show(fixtureState())

        compose.onNodeWithContentDescription(
            "Fixture Novel. Want to read. 64% read. No source linked.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Want to read").assertIsDisplayed()
        compose.onNodeWithText("64% read").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Fixture Novel. Want to read. 64% read. No source linked.",
        ).assertIsDisplayed()
    }

    @Test
    fun controlsMeetMinimumTargetAndFocusToolbarBeforeStories() {
        val firstFilterFocus = FocusRequester()
        lateinit var inputModeManager: InputModeManager
        show(fixtureState(), firstFilterFocus = firstFilterFocus) { inputModeManager = LocalInputModeManager.current }

        compose.runOnIdle {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            firstFilterFocus.requestFocus()
        }
        compose.onNodeWithContentDescription("Search your Library")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithContentDescription("Open Library filters")
            .assertHeightIsAtLeast(48.dp)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Switch to list view")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("library-story-story-1").assertIsFocused()
    }

    @Test
    fun actionsAndSelectionReportCanonicalValues() {
        var selected: StoryId? = null
        var displayMode: LibraryDisplayMode? = null
        show(
            state = fixtureState(),
            onStorySelected = { selected = it },
            onDisplayModeSelected = { displayMode = it },
        )

        compose.onNodeWithContentDescription("Switch to list view").performClick()
        compose.onNodeWithContentDescription(
            "Fixture Novel. Want to read. 64% read. No source linked.",
        ).performClick()

        assertEquals(LibraryDisplayMode.LIST, displayMode)
        assertEquals(StoryId("story-1"), selected)
    }

    @Test
    fun trueAndFilteredEmptyStatesRemainDistinct() {
        val state = mutableStateOf(fixtureState().copy(items = emptyList(), totalCount = 0))
        compose.setContent {
            HikariTheme {
                LibraryScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Your Library is empty").assertIsDisplayed()
        compose.onNodeWithText("Discover stories").assertIsDisplayed()

        compose.runOnIdle {
            state.value = fixtureState().copy(items = emptyList(), totalCount = 4, query = "missing")
        }
        compose.onNodeWithText("No stories match these filters").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").assertIsDisplayed()
    }

    @Test
    fun emptyActionsRemainReachableAfterToolbarControls() {
        val firstFilterFocus = FocusRequester()
        lateinit var inputModeManager: InputModeManager
        show(
            fixtureState().copy(items = emptyList(), totalCount = 3, query = "missing"),
            firstFilterFocus = firstFilterFocus,
        ) { inputModeManager = LocalInputModeManager.current }

        compose.runOnIdle {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            firstFilterFocus.requestFocus()
        }
        compose.onNodeWithContentDescription("Search your Library")
            .performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithContentDescription("Open Library filters")
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Switch to list view")
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Clear filters").assertIsFocused()
    }

    private fun show(
        state: LibraryUiState,
        onStorySelected: (StoryId) -> Unit = {},
        onDisplayModeSelected: (LibraryDisplayMode) -> Unit = {},
        firstFilterFocus: FocusRequester? = null,
        extraContent: @Composable () -> Unit = {},
    ) {
        compose.setContent {
            HikariTheme {
                extraContent()
                LibraryScreen(
                    state = state,
                    onQueryChange = {},
                    onStatusSelected = {},
                    onSourceFilterSelected = {},
                    onSortSelected = {},
                    onDisplayModeSelected = onDisplayModeSelected,
                    onClearFilters = {},
                    onDiscover = {},
                    onStorySelected = onStorySelected,
                    firstFilterFocusRequester = firstFilterFocus,
                )
            }
        }
    }
}

private fun fixtureState() = LibraryUiState(
    items = listOf(
        LibraryItemUiModel(
            storyId = StoryId("story-1"),
            title = "Fixture Novel",
            contentType = ContentType.WEB_NOVEL,
            coverUrl = null,
            status = LibraryStatus.WANT_TO_READ,
            sourceState = LibrarySourceState.NO_MAPPING,
            progressFraction = 0.64f,
            addedAt = 10L,
            updatedAt = 10L,
        ),
    ),
    totalCount = 1,
    statusCounts = mapOf(LibraryStatus.WANT_TO_READ to 1),
    loading = false,
)
