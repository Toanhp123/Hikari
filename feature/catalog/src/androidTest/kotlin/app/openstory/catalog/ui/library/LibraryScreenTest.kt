package app.openstory.catalog.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.library.LibraryStatus
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun metadataOnlyItemUsesAccessibleNonErrorSourceState() {
        compose.setContent {
            HikariTheme {
                LibraryScreen(
                    state = fixtureState(),
                    onStatusSelected = {},
                    onSortSelected = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Fixture Novel. Want to read. No source linked.",
        ).assertIsDisplayed()
        compose.onNodeWithText("No source linked").assertIsDisplayed()
    }

    @Test
    fun itemClickReportsCanonicalStoryIdentityOnly() {
        var selected: StoryId? = null
        compose.setContent {
            HikariTheme {
                LibraryScreen(
                    state = fixtureState(),
                    onStatusSelected = {},
                    onSortSelected = {},
                    onStorySelected = { selected = it },
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Fixture Novel. Want to read. No source linked.",
        ).performClick()
        assertEquals(StoryId("story-1"), selected)
    }

    @Test
    fun trueEmptyKeepsLibraryCopy() {
        compose.setContent {
            HikariTheme {
                LibraryScreen(
                    state = fixtureState().copy(items = emptyList()),
                    onStatusSelected = {},
                    onSortSelected = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithText("Your Library is empty.").assertIsDisplayed()
    }

    @Test
    fun filteredEmptyKeepsStatusCopy() {
        compose.setContent {
            HikariTheme {
                LibraryScreen(
                    state = fixtureState().copy(
                        items = emptyList(),
                        selectedStatus = LibraryStatus.READING,
                    ),
                    onStatusSelected = {},
                    onSortSelected = {},
                    onStorySelected = {},
                )
            }
        }

        compose.onNodeWithText("No stories with this status.").assertIsDisplayed()
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
            addedAt = 10L,
            updatedAt = 10L,
        ),
    ),
)
