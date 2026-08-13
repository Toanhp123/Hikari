package app.openstory.catalog.ui.story

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.chapters.ChapterListUiState
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
    fun resumeActionUsesProjectedReaderTarget() {
        var opened: ReaderTarget? = null
        val target = ReaderTarget(
            StoryId("story-1"), CanonicalChapterId("chapter-2"), ChapterReleaseId("release-2"),
        )
        setStoryContent(
            state = fixtureState().copy(resumeTarget = target),
            chapterState = ChapterListUiState(readableTargets = listOf(target)),
            onRead = { opened = it },
        )

        compose.onNodeWithText("Resume").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(target, opened)
    }

    @Test
    fun staleResumeDoesNotExposeDownloadWithoutReadableChapter() {
        val target = ReaderTarget(
            StoryId("story-1"), CanonicalChapterId("chapter-old"), ChapterReleaseId("release-old"),
        )
        setStoryContent(state = fixtureState().copy(resumeTarget = target))

        assertEquals(0, compose.onAllNodesWithText("Resume").fetchSemanticsNodes().size)
        compose.onNodeWithText("Read").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Download").fetchSemanticsNodes().size)
    }

    @Test
    fun libraryActionUsesSelectedStatus() {
        var status: LibraryStatus? = null
        setStoryContent(onLibraryStatusSelected = { status = it })

        compose.onNodeWithTag("story-library").assertHeightIsAtLeast(48.dp).performClick()
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
        compose.onNodeWithText("Source detail refresh failed: catalog.offline").assertIsDisplayed()
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
            onRetry = { retried = true },
        )

        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    private fun setStoryContent(
        state: StoryUiState = fixtureState(),
        onRetry: () -> Unit = {},
        onSourceSelected: (PluginId, String) -> Unit = { _, _ -> },
        onSectionSelected: (StorySection) -> Unit = {},
        onLibraryStatusSelected: (LibraryStatus?) -> Unit = {},
        onRead: (ReaderTarget) -> Unit = {},
        chapterState: ChapterListUiState? = null,
    ) {
        compose.setContent {
            HikariTheme {
                StoryScreen(
                    state = state,
                    onRetry = onRetry,
                    onSourceSelected = onSourceSelected,
                    onSectionSelected = onSectionSelected,
                    onLibraryStatusSelected = onLibraryStatusSelected,
                    onRead = onRead,
                    chapterState = chapterState,
                )
            }
        }
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
