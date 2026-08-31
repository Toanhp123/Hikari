package app.openstory.catalog.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.library.LibraryStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class LibrarySemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun titleAndUtilityShareTheScrollableHeaderBand() {
        compose.setContent {
            HikariTheme {
                LibraryScreen(
                    state = libraryState(
                        items = listOf(
                            LibraryItemUiModel(
                                storyId = StoryId("story"),
                                title = "Story",
                                contentType = ContentType.WEB_NOVEL,
                                coverUrl = null,
                                status = LibraryStatus.READING,
                                sourceState = LibrarySourceState.LINKED,
                                addedAt = 1L,
                                updatedAt = 1L,
                            ),
                        ),
                    ),
                    onQueryChange = {},
                    onStatusSelected = {},
                    onSourceFilterSelected = {},
                    onSortSelected = {},
                    onDisplayModeSelected = {},
                    onClearFilters = {},
                    onDiscover = {},
                    onStorySelected = {},
                    onUtilityRequested = {},
                )
            }
        }

        val titleBounds = compose.onNodeWithText("Library").fetchSemanticsNode().boundsInRoot
        val utilityBounds = compose.onNodeWithContentDescription("Open quick access")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(titleBounds.top < utilityBounds.bottom && utilityBounds.top < titleBounds.bottom)
        assertTrue(titleBounds.left <= 20f, "Library header must align with the shared 16dp gutter")
        assertTrue(utilityBounds.top <= 12f, "Library header must not inherit grid top padding")
    }

    @Test
    fun compactToolbarMovesFiltersIntoSheetAndTogglesView() {
        var selectedMode: LibraryDisplayMode? = null
        compose.setContent {
            HikariTheme {
                LibraryScreen(
                    state = libraryState(),
                    onQueryChange = {},
                    onStatusSelected = {},
                    onSourceFilterSelected = {},
                    onSortSelected = {},
                    onDisplayModeSelected = { selectedMode = it },
                    onClearFilters = {},
                    onResetFilters = {},
                    onDiscover = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Search your Library").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Open Library filters").performClick()
        compose.onNodeWithText("Library filters").assertExists()
        compose.onNodeWithText("Status").assertExists()
        compose.onNodeWithText("Source").assertExists()
        compose.onNodeWithText("Sort").assertExists()
        compose.onNodeWithText("Clear filters").assertIsDisplayed()
        compose.onNodeWithTag("library-source-unknown").assertDoesNotExist()

        compose.onNodeWithContentDescription("Switch to list view").performClick()
        assertTrue(selectedMode == LibraryDisplayMode.LIST)
    }

    @Test
    fun pendingMembershipDoesNotClaimZeroStatusCounts() {
        compose.setContent {
            HikariTheme {
                LibraryScreen(
                    state = LibraryUiState(),
                    onQueryChange = {},
                    onStatusSelected = {},
                    onSourceFilterSelected = {},
                    onSortSelected = {},
                    onDisplayModeSelected = {},
                    onClearFilters = {},
                    onDiscover = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Open Library filters").performClick()
        compose.onNodeWithText("All").assertIsDisplayed()
        compose.onNodeWithText("All 0").assertDoesNotExist()
        compose.onNodeWithText("Reading 0").assertDoesNotExist()
    }

    @Test
    fun gridUsesTheSharedPosterCard() {
        compose.setContent {
            HikariTheme {
                LibraryScreen(
                    state = libraryState(),
                    onQueryChange = {},
                    onStatusSelected = {},
                    onSourceFilterSelected = {},
                    onSortSelected = {},
                    onDisplayModeSelected = {},
                    onClearFilters = {},
                    onDiscover = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithTag("story-poster-card", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun localResolvingKeepsToolbarInteractiveAndUsesCollectionSkeleton() {
        compose.setContent {
            HikariTheme {
                LibraryScreen(
                    state = libraryState(collection = LibraryCollectionState.Resolving),
                    onQueryChange = {},
                    onStatusSelected = {},
                    onSourceFilterSelected = {},
                    onSortSelected = {},
                    onDisplayModeSelected = {},
                    onClearFilters = {},
                    onDiscover = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Search your Library").assertIsDisplayed()
        compose.onNodeWithTag("library-collection-skeleton").assertIsDisplayed()
        compose.onNodeWithText("Loading your Library").assertDoesNotExist()
    }

    @Test
    fun unknownMappingUsesSkeletonAndOmitsSourceAccessibilityClaim() {
        val item = libraryItem().copy(sourceState = LibrarySourceState.UNKNOWN)
        compose.setContent {
            HikariTheme {
                LibraryScreen(
                    state = libraryState(items = listOf(item)),
                    onQueryChange = {},
                    onStatusSelected = {},
                    onSourceFilterSelected = {},
                    onSortSelected = {},
                    onDisplayModeSelected = {},
                    onClearFilters = {},
                    onDiscover = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithTag("library-source-skeleton-story", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Story. Reading.").assertIsDisplayed()
        compose.onNodeWithText("Source status unavailable").assertDoesNotExist()
    }
}

private fun libraryState(
    items: List<LibraryItemUiModel> = listOf(libraryItem()),
    collection: LibraryCollectionState = LibraryCollectionState.Ready(items),
) = LibraryUiState(
    content = ContentState.Ready(
        LibraryContent(
            totalCount = items.size,
            statusCounts = LibraryStatus.entries.associateWith { status -> items.count { it.status == status } },
            collection = collection,
        ),
    ),
)

private fun libraryItem() = LibraryItemUiModel(
    storyId = StoryId("story"),
    title = "Story",
    contentType = ContentType.WEB_NOVEL,
    coverUrl = null,
    status = LibraryStatus.READING,
    sourceState = LibrarySourceState.LINKED,
    addedAt = 1L,
    updatedAt = 1L,
)
