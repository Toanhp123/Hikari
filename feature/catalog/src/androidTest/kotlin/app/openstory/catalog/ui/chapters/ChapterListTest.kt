package app.openstory.catalog.ui.chapters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.downloads.DownloadState
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
        compose.onNodeWithText("org.mangadex.content").assertDoesNotExist()
        compose.onNodeWithContentDescription("Chapter 10, 2 releases, unread").performClick()
        compose.onNodeWithText("org.mangadex.content").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("English", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test
    fun unavailableFilterUsesSharedChipAction() {
        var showUnavailable = false
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = fixtureState().copy(showTombstones = false),
                    actions = ChapterListActions(
                        onTombstonesVisible = { showUnavailable = it },
                    ),
                )
            }
        }

        compose.onNodeWithText("Unavailable").performClick()

        kotlin.test.assertTrue(showUnavailable)
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
    fun releaseActionsKeepReleaseIdentityAndMeetTouchTargets() {
        val state = fixtureState().copy(chapters = fixtureState().chapters.map { it.copy(expanded = true) })
        var readTarget: ReaderTarget? = null
        var downloaded: ChapterReleaseId? = null
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = state,
                    actions = ChapterListActions(
                        onRead = { readTarget = it },
                        downloadActions = app.openstory.catalog.ui.download.DownloadActions(
                            onDownload = { downloaded = it },
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithTag("chapter-read-release:10:a")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNodeWithTag("chapter-download-release:10:a")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        kotlin.test.assertEquals(
            ReaderTarget(state.storyId!!, CanonicalChapterId("chapter:10"), ChapterReleaseId("release:10:a")),
            readTarget,
        )
        kotlin.test.assertEquals(ChapterReleaseId("release:10:a"), downloaded)
    }


    @Test
    fun listOnlyReleaseHasNoReadOrDownloadAndBulkDownloadSkipsIt() {
        val base = fixtureState()
        val state = base.copy(
            chapters = base.chapters.map { chapter ->
                chapter.copy(
                    expanded = true,
                    releases = chapter.releases.mapIndexed { index, release ->
                        release.copy(readerCapable = index != 0)
                    },
                )
            },
        )
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

        compose.onAllNodesWithTag("chapter-read-release:10:a").assertCountEquals(0)
        compose.onAllNodesWithTag("chapter-download-release:10:a").assertCountEquals(0)
        compose.onNodeWithText("List only", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Download visible").performClick()
        compose.onNodeWithText("Download chapter").performClick()

        val readable = listOf(ChapterReleaseId("release:10:b"))
        kotlin.test.assertEquals(readable, filtered)
        kotlin.test.assertEquals(readable, range)
    }


    @Test
    fun completedListOnlyReleaseRemainsReadableOfflineWithoutNewDownload() {
        val base = fixtureState()
        val state = base.copy(
            chapters = base.chapters.map { chapter ->
                chapter.copy(
                    expanded = true,
                    releases = chapter.releases.mapIndexed { index, release ->
                        release.copy(readerCapable = index != 0)
                    },
                )
            },
        )
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = state,
                    actions = ChapterListActions(
                        downloadState = { releaseId ->
                            DownloadState.COMPLETED.takeIf { releaseId == ChapterReleaseId("release:10:a") }
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag("chapter-read-release:10:a").assertIsDisplayed()
        compose.onNodeWithTag("chapter-download-release:10:a").assertIsDisplayed()
        compose.onNodeWithText("Offline only", useUnmergedTree = true).assertIsDisplayed()
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
    loading = false,
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
                    readerCapable = true,
                ),
                ChapterReleaseUiModel(
                    id = ChapterReleaseId("release:10:b"),
                    pluginId = PluginId("org.example.content"),
                    sourceName = "org.example.content",
                    languageLabel = "Vietnamese",
                    publishedAtEpochMillis = 2L,
                    readerCapable = true,
                ),
            ),
        ),
    ),
)
