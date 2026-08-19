package app.openstory.catalog.ui.chapters

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.download.DownloadActions
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
    fun chapterFailureHidesMachineCode() {
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = fixtureState().copy(failure = "plugin.mangadex_http_status"),
                    actions = ChapterListActions(),
                )
            }
        }

        compose.onNodeWithText("Couldn't refresh chapters.").assertIsDisplayed()
        compose.onNodeWithText("plugin.mangadex_http_status").assertDoesNotExist()
    }

    @Test
    fun canonicalGroupsStayCollapsedUntilExpanded() {
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = fixtureState(),
                    actions = ChapterListActions(),
                )
            }
        }

        compose.onNodeWithText("1 chapter").assertIsDisplayed()
        compose.onNodeWithText("Chapter 10").assertIsDisplayed()
        compose.onNodeWithText("The Locked Constellation").assertIsDisplayed()
        compose.onNodeWithText("MangaDex").assertDoesNotExist()

        compose.onNodeWithText("Chapter 10")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNodeWithText("MangaDex").assertIsDisplayed()
        compose.onNodeWithText("English", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Chapter 10").performClick()
        compose.onNodeWithText("MangaDex").assertDoesNotExist()
    }

    @Test
    fun paginationLimitsChapterGroupsToFiftyPerPage() {
        val base = fixtureState()
        val chapters = (1..51).map { number ->
            base.chapters.single().copy(
                id = CanonicalChapterId("chapter:$number"),
                label = "Chapter $number",
                releases = emptyList(),
                title = null,
            )
        }
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = base.copy(chapterCount = chapters.size, chapters = chapters),
                    actions = ChapterListActions(),
                )
            }
        }

        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithText("Chapter 51").assertDoesNotExist()

        compose.onNodeWithContentDescription("Next page").performClick()

        compose.onAllNodesWithText("2 / 2").assertCountEquals(1)
        compose.onNodeWithText("Chapter 51").assertIsDisplayed()
        compose.onNodeWithText("Chapter 1").assertDoesNotExist()
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

        compose.onNodeWithContentDescription("Chapter options").performClick()
        compose.onNodeWithText("Unavailable").performClick()

        kotlin.test.assertTrue(showUnavailable)
    }

    @Test
    fun visibleFilterExposesBulkDownloadCommand() {
        val state = fixtureState()
        var filtered = emptyList<ChapterReleaseId>()
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = state,
                    actions = ChapterListActions(
                        onDownloadFiltered = { filtered = it },
                    ),
                )
            }
        }

        compose.onNodeWithContentDescription("Chapter options").performClick()
        compose.onNodeWithText("Download visible").performClick()

        kotlin.test.assertEquals(state.chapters.single().releases.map { it.id }, filtered)
    }

    @Test
    fun releaseActionsKeepReleaseIdentityAndMeetTouchTargets() {
        val state = fixtureState()
        var readTarget: ReaderTarget? = null
        var downloaded: ChapterReleaseId? = null
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = state,
                    actions = ChapterListActions(
                        onRead = { readTarget = it },
                        downloadActions = DownloadActions(onDownload = { downloaded = it }),
                    ),
                )
            }
        }

        compose.onNodeWithText("Chapter 10").performClick()
        compose.onNodeWithTag("chapter-read-release:10:a")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNodeWithTag("chapter-more-release:10:a")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNodeWithTag("chapter-download-release:10:a").performClick()

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
                    releases = chapter.releases.mapIndexed { index, release ->
                        release.copy(readerCapable = index != 0, downloadCapable = index != 0)
                    },
                )
            },
        )
        var filtered = emptyList<ChapterReleaseId>()
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = state,
                    actions = ChapterListActions(onDownloadFiltered = { filtered = it }),
                )
            }
        }

        compose.onNodeWithText("Chapter 10").performClick()
        compose.onAllNodesWithTag("chapter-read-release:10:a").assertCountEquals(0)
        compose.onNodeWithTag("chapter-more-release:10:a").performClick()
        compose.onAllNodesWithTag("chapter-download-release:10:a").assertCountEquals(0)
        compose.onNodeWithText("List only", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Chapter options").performClick()
        compose.onNodeWithText("Download visible").performClick()

        kotlin.test.assertEquals(listOf(ChapterReleaseId("release:10:b")), filtered)
    }

    @Test
    fun onlineOnlyReleaseKeepsReadButHidesDownloadAndBulkSkipsIt() {
        val base = fixtureState()
        val state = base.copy(
            chapters = base.chapters.map { chapter ->
                chapter.copy(
                    releases = chapter.releases.mapIndexed { index, release ->
                        release.copy(downloadCapable = index != 0)
                    },
                )
            },
        )
        var filtered = emptyList<ChapterReleaseId>()
        compose.setContent {
            HikariTheme {
                ChapterList(
                    state = state,
                    actions = ChapterListActions(onDownloadFiltered = { filtered = it }),
                )
            }
        }

        compose.onNodeWithText("Chapter 10").performClick()
        compose.onNodeWithTag("chapter-read-release:10:a").assertIsDisplayed()
        compose.onNodeWithTag("chapter-more-release:10:a").performClick()
        compose.onAllNodesWithTag("chapter-download-release:10:a").assertCountEquals(0)
        compose.onNodeWithText("Online only", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Chapter options").performClick()
        compose.onNodeWithText("Download visible").performClick()

        kotlin.test.assertEquals(listOf(ChapterReleaseId("release:10:b")), filtered)
    }

    @Test
    fun completedListOnlyReleaseRemainsReadableOfflineAndExposesRemoval() {
        val base = fixtureState()
        val state = base.copy(
            chapters = base.chapters.map { chapter ->
                chapter.copy(
                    releases = chapter.releases.mapIndexed { index, release ->
                        release.copy(readerCapable = index != 0, downloadCapable = index != 0)
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

        compose.onNodeWithText("Chapter 10").performClick()
        compose.onNodeWithTag("chapter-read-release:10:a").assertIsDisplayed()
        compose.onNodeWithText("Offline only", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("chapter-more-release:10:a").performClick()
        compose.onNodeWithTag("chapter-download-release:10:a").assertIsDisplayed()
        compose.onNodeWithText("Remove offline").assertIsDisplayed()
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
    chapterCount = 1,
    chapters = listOf(
        ChapterItemUiModel(
            id = CanonicalChapterId("chapter:10"),
            label = "Chapter 10",
            tombstoned = false,
            releases = listOf(
                ChapterReleaseUiModel(
                    id = ChapterReleaseId("release:10:a"),
                    pluginId = PluginId("org.mangadex.content"),
                    sourceName = "MangaDex",
                    languageLabel = "English",
                    publishedAtEpochMillis = 1L,
                    readerCapable = true,
                    downloadCapable = true,
                ),
                ChapterReleaseUiModel(
                    id = ChapterReleaseId("release:10:b"),
                    pluginId = PluginId("org.example.content"),
                    sourceName = "Content",
                    languageLabel = "Vietnamese",
                    publishedAtEpochMillis = 2L,
                    readerCapable = true,
                    downloadCapable = true,
                ),
            ),
            title = "The Locked Constellation",
        ),
    ),
)
