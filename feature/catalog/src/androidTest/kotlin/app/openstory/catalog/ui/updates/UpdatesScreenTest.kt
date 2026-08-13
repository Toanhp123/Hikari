package app.openstory.catalog.ui.updates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.activity.LibraryActivityItem
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdatesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun updateExposesSourceLanguageAndBothCanonicalNavigationActions() {
        var story: StoryId? = null
        var reader: ReaderTarget? = null
        compose.setContent {
            HikariTheme { UpdatesScreen(updatesFixture(), { story = it }, { reader = it }) }
        }

        compose.onNodeWithText("Aug 3, 2025").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "The Fox of the Moonlit Archive, Chapter 12, content.mangadex, en",
        ).performClick()
        assertEquals(StoryId("story-update"), story)

        compose.onNodeWithText("Read").performClick()
        assertEquals("release-update", reader?.releaseId?.value)
    }
}

private fun updatesFixture(): UpdatesUiState {
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
