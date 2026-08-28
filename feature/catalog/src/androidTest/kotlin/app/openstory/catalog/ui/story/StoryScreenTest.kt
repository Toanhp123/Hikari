package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.chapters.ChapterListContent
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.library.LibraryStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class StoryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sectionTabsSelectAndAnnounceActiveSection() {
        var selected: StorySection? = null
        setStoryContent(onSectionSelected = { selected = it })

        compose.onNodeWithTag("story-tab-overview").assertIsSelected().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Active section"))
        compose.onNodeWithTag("story-tab-sources").performClick()

        assertEquals(StorySection.SOURCES, selected)
    }

    @Test
    fun overviewExposesPullRefreshAction() {
        var refreshCalls = 0
        setStoryContent(onRefresh = { refreshCalls += 1 })

        val refreshAction = compose.onNodeWithTag("story-overview-pull-refresh")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Refresh" }

        assertTrue(refreshAction.action())
        assertEquals(1, refreshCalls)
    }

    @Test
    fun sourcesExposePullRefreshAndNoManualRefreshIcon() {
        var refreshCalls = 0
        setStoryContent(
            state = fixtureState().copy(selectedSection = StorySection.SOURCES),
            onRefresh = { refreshCalls += 1 },
        )

        val refreshAction = compose.onNodeWithTag("story-sources-pull-refresh")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Refresh" }

        assertTrue(refreshAction.action())
        assertEquals(1, refreshCalls)
        compose.onAllNodesWithTag("story-source-refresh").assertCountEquals(0)
    }

    @Test
    fun chaptersDoNotExposePullRefresh() {
        setStoryContent(state = fixtureState().copy(selectedSection = StorySection.CHAPTERS))

        compose.onAllNodesWithTag("story-overview-pull-refresh").assertCountEquals(0)
        compose.onAllNodesWithTag("story-sources-pull-refresh").assertCountEquals(0)
    }

    @Test
    fun resumeActionUsesProjectedReaderTarget() {
        var opened: ReaderTarget? = null
        val target = ReaderTarget(
            StoryId("story-1"), CanonicalChapterId("chapter-2"), ChapterReleaseId("release-2"),
        )
        setStoryContent(
            state = fixtureState().copy(resumeTarget = target),
            chapterState = readyChapterState(
                chapterCount = 1,
                readableTargets = listOf(target),
                releaseTargets = listOf(target),
            ),
            onRead = { opened = it },
        )

        compose.onNodeWithText("Resume").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(target, opened)
    }


    @Test
    fun existingProgressCanResumeOfflineWithoutLiveReaderCapability() {
        val target = ReaderTarget(
            StoryId("story-1"), CanonicalChapterId("chapter-offline"), ChapterReleaseId("release-offline"),
        )
        var opened: ReaderTarget? = null
        setStoryContent(
            state = fixtureState().copy(resumeTarget = target),
            chapterState = readyChapterState(
                chapterCount = 1,
                releaseTargets = listOf(target),
            ),
            onRead = { opened = it },
        )

        compose.onNodeWithText("Resume").performClick()

        assertEquals(target, opened)
    }

    @Test
    fun compactHeroKeepsReadAndMoreActionsBesideCover() {
        val target = ReaderTarget(
            StoryId("story-1"), CanonicalChapterId("chapter-2"), ChapterReleaseId("release-2"),
        )
        setStoryContent(
            state = fixtureState(),
            chapterState = readyChapterState(
                chapterCount = 1,
                readableTargets = listOf(target),
                releaseTargets = listOf(target),
            ),
            modifier = Modifier.requiredWidth(320.dp),
        )

        val coverBounds = compose.onNodeWithTag("story-hero-cover").fetchSemanticsNode().boundsInRoot
        val readBounds = compose.onNodeWithTag("story-read").fetchSemanticsNode().boundsInRoot
        val moreBounds = compose.onNodeWithTag("story-more").fetchSemanticsNode().boundsInRoot

        assertTrue(readBounds.left >= coverBounds.right)
        assertTrue(moreBounds.left >= coverBounds.right)
        assertTrue(moreBounds.top < readBounds.bottom && moreBounds.bottom > readBounds.top)
        compose.onNodeWithTag("story-download").assertDoesNotExist()
        compose.onNodeWithTag("story-library").assertDoesNotExist()
    }

    @Test
    fun moreActionsSheetOwnsDownloadAndLibraryActions() {
        val target = ReaderTarget(
            StoryId("story-1"), CanonicalChapterId("chapter-2"), ChapterReleaseId("release-2"),
        )
        var downloaded: ChapterReleaseId? = null
        var status: LibraryStatus? = null
        setStoryContent(
            state = fixtureState(),
            chapterState = readyChapterState(
                chapterCount = 1,
                readableTargets = listOf(target),
                downloadableTargets = listOf(target),
                releaseTargets = listOf(target),
            ),
            onDownload = { downloaded = it },
            onLibraryStatusSelected = { status = it },
        )

        compose.onNodeWithTag("story-more").performClick()
        compose.onNodeWithText("Story actions").assertIsDisplayed()
        compose.onNodeWithTag("story-download").performClick()
        assertEquals(ChapterReleaseId("release-2"), downloaded)

        compose.onNodeWithTag("story-more").performClick()
        compose.onNodeWithText("Reading").performClick()
        assertEquals(LibraryStatus.READING, status)
    }

    @Test
    fun onlineOnlyReaderTargetDoesNotDownloadAnotherReleaseFromHero() {
        val onlineTarget = ReaderTarget(
            StoryId("story-1"), CanonicalChapterId("chapter-1"), ChapterReleaseId("release-online"),
        )
        val otherDownload = ReaderTarget(
            StoryId("story-1"), CanonicalChapterId("chapter-2"), ChapterReleaseId("release-offline"),
        )
        setStoryContent(
            state = fixtureState(),
            chapterState = readyChapterState(
                chapterCount = 2,
                readableTargets = listOf(onlineTarget),
                downloadableTargets = listOf(otherDownload),
                releaseTargets = listOf(onlineTarget, otherDownload),
            ),
        )

        compose.onNodeWithTag("story-more").performClick()

        compose.onNodeWithTag("story-download").assertDoesNotExist()
        compose.onNodeWithTag("story-library-reading").assertIsDisplayed()
    }

    @Test
    fun staleResumeDoesNotExposeDownloadWithoutReadableChapter() {
        val target = ReaderTarget(
            StoryId("story-1"), CanonicalChapterId("chapter-old"), ChapterReleaseId("release-old"),
        )
        setStoryContent(state = fixtureState().copy(resumeTarget = target))

        assertEquals(0, compose.onAllNodesWithText("Resume").fetchSemanticsNodes().size)
        compose.onNodeWithTag("story-chapters-checking").assertIsDisplayed()
        compose.onAllNodesWithTag("story-find-source").assertCountEquals(0)
        assertEquals(0, compose.onAllNodesWithText("Download").fetchSemanticsNodes().size)
    }

    @Test
    fun missingReadableReleaseOffersFindSourceInsteadOfDisabledRead() {
        var selected: StorySection? = null
        setStoryContent(
            state = fixtureState(),
            chapterState = readyChapterState(
                chapterCount = 1,
                releaseTargets = listOf(
                    ReaderTarget(
                        StoryId("story-1"),
                        CanonicalChapterId("chapter-1"),
                        ChapterReleaseId("release-1"),
                    ),
                ),
            ),
            onSectionSelected = { selected = it },
        )

        compose.onNodeWithTag("story-read").assertDoesNotExist()
        compose.onNodeWithTag("story-find-source").assertIsDisplayed().performClick()

        assertEquals(StorySection.SOURCES, selected)
    }

    @Test
    fun libraryActionUsesSelectedStatus() {
        var status: LibraryStatus? = null
        setStoryContent(onLibraryStatusSelected = { status = it })

        compose.onNodeWithTag("story-more").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Reading").performClick()

        assertEquals(LibraryStatus.READING, status)
    }

    @Test
    fun cachedFailureDisablesRetryWhileRefreshing() {
        setStoryContent(state = fixtureState(failed = true).copy(refreshing = true))

        compose.onNodeWithTag("story-retry").assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
    }

    @Test
    fun cachedStoryAndSourceFailureRenderTogether() {
        setStoryContent(state = fixtureState(failed = true))

        compose.onNodeWithText("Fixture Novel").assertIsDisplayed()
        compose.onNodeWithText("Couldn't refresh story details.").assertIsDisplayed()
        compose.onNodeWithText("catalog.offline").assertDoesNotExist()
    }

    @Test
    fun sourceSectionKeepsCatalogSelectionAsUiAction() {
        var selected: Pair<PluginId, String>? = null
        setStoryContent(
            state = fixtureState().copy(selectedSection = StorySection.SOURCES),
            onSourceSelected = { pluginId, sourceId -> selected = pluginId to sourceId },
        )

        compose.onNodeWithTag("story-source-catalog.a-source-a").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(PluginId("catalog.a") to "source-a", selected)
    }

    @Test
    fun noContentRetryableFailureKeepsRetryAction() {
        var retried = false
        setStoryContent(
            state = fixtureState(failed = true).copy(story = null),
            onRefresh = { retried = true },
        )

        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun mediumOverviewRendersDescriptionOnlyOnce() {
        setStoryContent(modifier = Modifier.requiredWidth(700.dp))

        compose.onAllNodesWithText("A quiet fantasy about an impossible archive.")
            .assertCountEquals(1)
    }

    @Test
    fun mediumSourcesRemainInContentPane() {
        setStoryContent(
            state = fixtureState().copy(selectedSection = StorySection.SOURCES),
            modifier = Modifier.requiredWidth(700.dp),
        )

        compose.onNodeWithTag("story-summary-pane").assertIsDisplayed()
        compose.onNodeWithTag("story-content-pane").assertIsDisplayed()
        compose.onNodeWithTag("story-source-catalog.a-source-a").assertIsDisplayed()
    }

    private fun setStoryContent(
        state: StoryUiState = fixtureState(),
        onRefresh: () -> Unit = {},
        onSourceSelected: (PluginId, String) -> Unit = { _, _ -> },
        onSectionSelected: (StorySection) -> Unit = {},
        onLibraryStatusSelected: (LibraryStatus?) -> Unit = {},
        onRead: (ReaderTarget) -> Unit = {},
        onDownload: (ChapterReleaseId) -> Unit = {},
        chapterState: ChapterListUiState? = null,
        modifier: Modifier = Modifier,
    ) {
        compose.setContent {
            HikariTheme {
                StoryScreen(
                    state = state,
                    onRefresh = onRefresh,
                    onSourceSelected = onSourceSelected,
                    onSectionSelected = onSectionSelected,
                    onLibraryStatusSelected = onLibraryStatusSelected,
                    onRead = onRead,
                    onDownload = onDownload,
                    chapterState = chapterState,
                    modifier = modifier,
                )
            }
        }
        compose.waitForIdle()
    }
}

private fun fixtureState(failed: Boolean = false): StoryUiState {
    val storyId = StoryId("story-1")
    val entry = CatalogEntry(
        storyId = storyId,
        pluginId = PluginId("catalog.a"),
        sourceId = "source-a",
        title = "Fixture Novel",
        authors = setOf("Fixture Author"),
        description = "A quiet fantasy about an impossible archive.",
        genres = setOf("Fantasy"),
        contentType = ContentType.WEB_NOVEL,
        languageTags = setOf("en"),
    )
    return StoryUiState(
        storyId = storyId,
        story = StoryUiModel(
            storyId = storyId,
            preferredTitle = entry.title,
            contentType = entry.contentType,
            aliases = setOf("Moonlit Archive"),
            description = entry.description,
            authors = entry.authors,
            genres = entry.genres,
            languageTags = entry.languageTags,
            sources = listOf(entry),
        ),
        selectedSource = StorySourceIdentity(entry.pluginId, entry.sourceId),
        failure = if (failed) StoryRefreshFailure("catalog.offline", true) else null,
    )
}


private fun readyChapterState(
    chapterCount: Int,
    readableTargets: List<ReaderTarget> = emptyList(),
    downloadableTargets: List<ReaderTarget> = emptyList(),
    releaseTargets: List<ReaderTarget> = emptyList(),
    readerAvailabilityResolved: Boolean = true,
): ChapterListUiState = ChapterListUiState(
    storyId = StoryId("story-1"),
    content = ContentState.Ready(
        ChapterListContent(
            chapters = emptyList(),
            readableTargets = readableTargets,
            downloadableTargets = downloadableTargets,
            releaseTargets = releaseTargets,
            chapterCount = chapterCount,
            readerAvailabilityResolved = readerAvailabilityResolved,
        ),
    ),
)
