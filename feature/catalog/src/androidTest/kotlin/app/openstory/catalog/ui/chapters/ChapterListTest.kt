package app.openstory.catalog.ui.chapters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Rule
import org.junit.Test

class ChapterListTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun canonicalRowsExpandReleasesAndExposeAccessibility() {
        var state by mutableStateOf(fixtureState())
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = state,
                    actions = ChapterListActions(
                        onToggleExpanded = { chapterId ->
                            state = state.copy(
                                chapters = state.chapters.map { chapter ->
                                    if (chapter.id == chapterId) chapter.copy(expanded = !chapter.expanded) else chapter
                                },
                            )
                        },
                    ),
                )
            }
        }

        compose.onNodeWithText("2 unread chapters").assertIsDisplayed()
        compose.onNodeWithText("org.mangadex.content · English").assertDoesNotExist()
        compose.onNodeWithContentDescription("Chapter 10, 2 releases, unread").performClick()
        compose.onNodeWithText("org.mangadex.content · English").assertIsDisplayed()
    }
    @Test
    fun visibleFilterAndChapterRangeExposeDownloadCommands() {
        val state = fixtureState().copy(chapters = fixtureState().chapters.map { it.copy(expanded = true) })
        var filtered = emptyList<ChapterReleaseId>()
        var range = emptyList<ChapterReleaseId>()
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = state,
                    actions = ChapterListActions(
                        onDownloadFiltered = { filtered = it },
                        onDownloadRange = { range = it },
                    ),
                )
            }
        }

        compose.onNodeWithText("Download visible").performClick()
        compose.onNodeWithText("Download chapter").performClick()

        val expected = state.chapters.single().releases.map { it.id }
        kotlin.test.assertEquals(expected, filtered)
        kotlin.test.assertEquals(expected, range)
    }

    @Test
    fun emptyChapterListKeepsExistingCopy() {
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = fixtureState().copy(chapters = emptyList()),
                    actions = ChapterListActions(),
                )
            }
        }

        compose.onNodeWithText("No chapters available").assertIsDisplayed()
    }
}

private fun fixtureState() = ChapterListUiState(
    storyId = StoryId("story:chapter-list-test"),
    unreadCount = 2,
    chapters = listOf(
        ChapterItemUiModel(
            id = CanonicalChapterId("chapter:10"),
            label = "Chapter 10",
            tombstoned = false,
            expanded = false,
            releases = listOf(
                ChapterReleaseUiModel(
                    id = ChapterReleaseId("release:10:a"),
                    pluginId = PluginId("org.mangadex.content"),
                    sourceName = "org.mangadex.content",
                    languageLabel = "English",
                    publishedAtEpochMillis = 1L,
                ),
                ChapterReleaseUiModel(
                    id = ChapterReleaseId("release:10:b"),
                    pluginId = PluginId("org.example.content"),
                    sourceName = "org.example.content",
                    languageLabel = "Vietnamese",
                    publishedAtEpochMillis = 2L,
                ),
            ),
        ),
    ),
)
