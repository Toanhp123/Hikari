package app.openstory.catalog.ui.chapters

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.semantics.SemanticsActions
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.downloads.DownloadState
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChapterListScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chapters() = capture(false, "chapters.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun offlineCachedChapters() = capture(true, "offline-cached-chapters.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactChapterGroupsExposeIdentityAndExpandSourcesOnDemand() {
        val releaseId = ChapterReleaseId("release-12")
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                ChapterList(
                    state = ChapterListUiState(
                        storyId = StoryId("moonlit"),
                        chapterCount = 1,
                        loading = false,
                        chapters = listOf(
                            ChapterItemUiModel(
                                id = CanonicalChapterId("chapter-12"),
                                label = "Chapter 12",
                                tombstoned = false,
                                releases = listOf(
                                    ChapterReleaseUiModel(
                                        releaseId,
                                        PluginId("org.mangadex.content"),
                                        "MangaDex",
                                        "English",
                                        1_786_560_000_000L,
                                        true,
                                    ),
                                ),
                                title = "The Locked Constellation",
                            ),
                        ),
                    ),
                    actions = ChapterListActions(),
                )
            }
        }

        compose.onNodeWithText("1 chapter").assertIsDisplayed()
        compose.onNodeWithText("Chapter 12").assertIsDisplayed()
        compose.onNodeWithText("The Locked Constellation").assertIsDisplayed()
        compose.onNodeWithText("MangaDex").assertDoesNotExist()
        compose.onNodeWithText("Chapter 12").performClick()
        compose.onNodeWithText("MangaDex").assertIsDisplayed()
    }


    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chapterControlsStayCompactUntilOptionsSheetOpens() {
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                ChapterList(
                    state = ChapterListUiState(storyId = StoryId("moonlit"), chapterCount = 1, loading = false),
                    actions = ChapterListActions(),
                )
            }
        }

        compose.onNodeWithText("All").assertDoesNotExist()
        compose.onNodeWithText("Multi-source").assertDoesNotExist()
        compose.onNodeWithText("Unavailable").assertDoesNotExist()
        compose.onNodeWithText("Download visible").assertDoesNotExist()

        compose.onNodeWithContentDescription("Chapter options").performClick()

        compose.onNodeWithText("Chapter options").assertIsDisplayed()
        compose.onNodeWithText("All").assertIsDisplayed()
        compose.onNodeWithText("Multi-source").assertIsDisplayed()
        compose.onNodeWithText("Unavailable").assertIsDisplayed()
        compose.onNodeWithText("Download visible").assertIsDisplayed()
    }


    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chapterHeaderOwnsTheOnlyPaginationControl() {
        val chapters = (1..51).map { number ->
            ChapterItemUiModel(
                id = CanonicalChapterId("chapter-$number"),
                label = "Chapter $number",
                tombstoned = false,
                releases = emptyList(),
            )
        }
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                ChapterList(
                    state = ChapterListUiState(
                        storyId = StoryId("moonlit"),
                        chapterCount = chapters.size,
                        loading = false,
                        chapters = chapters,
                    ),
                    actions = ChapterListActions(),
                )
            }
        }

        compose.onAllNodesWithText("1 / 2").assertCountEquals(1)
        compose.onNodeWithContentDescription("Chapter options").assertIsDisplayed()
    }


    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun stickyChapterHeaderKeepsTopBreathingRoomAfterScroll() {
        val chapters = (1..20).map { number ->
            ChapterItemUiModel(
                id = CanonicalChapterId("chapter-$number"),
                label = "Chapter $number",
                tombstoned = false,
                releases = emptyList(),
            )
        }
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                ChapterList(
                    state = ChapterListUiState(
                        storyId = StoryId("moonlit"),
                        chapterCount = chapters.size,
                        loading = false,
                        chapters = chapters,
                    ),
                    actions = ChapterListActions(),
                    contentPadding = PaddingValues(16.dp),
                )
            }
        }

        val listTop = compose.onNodeWithTag("chapter-list").fetchSemanticsNode().boundsInRoot.top
        val expectedGapPx = with(compose.density) { 16.dp.toPx() }
        val initialHeaderTop = compose.onNodeWithText("Chapters").fetchSemanticsNode().boundsInRoot.top
        assertEquals(expectedGapPx, initialHeaderTop - listTop, 1f)

        compose.onNodeWithTag("chapter-list").performScrollToIndex(10)

        val stickyHeaderTop = compose.onNodeWithText("Chapters").fetchSemanticsNode().boundsInRoot.top
        assertEquals(expectedGapPx, stickyHeaderTop - listTop, 1f)
        compose.onNodeWithTag("hikari-bottom-separation-shadow").assertIsDisplayed()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chaptersExposeSharedPullToRefreshAction() {
        var refreshes = 0
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                ChapterList(
                    state = ChapterListUiState(storyId = StoryId("moonlit"), loading = false),
                    actions = ChapterListActions(onRefresh = { refreshes += 1 }),
                )
            }
        }

        val refreshAction = compose.onNodeWithTag("chapter-pull-refresh")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { action -> action.label == "Refresh" }

        assertTrue(refreshAction.action())
        assertEquals(1, refreshes)
    }

    private fun capture(offline: Boolean, fileName: String) {
        val releaseId = ChapterReleaseId("release-12")
        val state = ChapterListUiState(
            storyId = StoryId("moonlit"),
            chapterCount = 2,
            chapters = listOf(
                ChapterItemUiModel(
                    id = CanonicalChapterId("chapter-12"),
                    label = "Chapter 12",
                    tombstoned = false,
                    releases = listOf(
                        ChapterReleaseUiModel(
                            releaseId,
                            PluginId("mangadex"),
                            "MangaDex",
                            "English",
                            1_786_560_000_000L,
                            true,
                        ),
                    ),
                    title = "The Locked Constellation",
                ),
                ChapterItemUiModel(
                    id = CanonicalChapterId("chapter-11"),
                    label = "Chapter 11",
                    tombstoned = false,
                    releases = emptyList(),
                    title = "A Fox at Dawn",
                ),
            ),
        )
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                ChapterList(
                    state,
                    ChapterListActions(
                        downloadState = { if (offline) DownloadState.COMPLETED else null },
                        downloadActions = DownloadActions(),
                    ),
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/chapters/$fileName")
    }
}
