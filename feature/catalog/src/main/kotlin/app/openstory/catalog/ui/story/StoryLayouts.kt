package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import app.openstory.catalog.ui.chapters.ChapterListActions
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.designsystem.theme.hikariLayoutRatios
import app.openstory.library.LibraryStatus

@Composable
internal fun MediumStoryLayout(
    state: StoryUiState,
    story: StoryUiModel,
    readerTarget: ReaderTarget?,
    isResume: Boolean,
    downloadableReleaseId: ChapterReleaseId?,
    onRefresh: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    onSectionSelected: (StorySection) -> Unit,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onFindSource: () -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
    mappingState: MappingUiState?,
    mappingActions: MappingActions,
    chapterState: ChapterListUiState?,
    chapterActions: ChapterListActions,
) {
    Row(Modifier.fillMaxSize()) {
        Column(storyPane(MaterialTheme.hikariLayoutRatios.detailSummaryPaneWeight, "story-summary-pane", 0f)) {
            StoryHero(
                story, state.libraryStatus, readerTarget, isResume, downloadableReleaseId,
                onLibraryStatusSelected, onRead, onFindSource, onDownload, narrow = true,
            )
            if (state.selectedSection != StorySection.OVERVIEW) {
                StoryOverview(story, compact = true, modifier = Modifier.weight(1f))
            }
        }
        Column(storyPane(MaterialTheme.hikariLayoutRatios.detailContentPaneWeight, "story-content-pane", 1f)) {
            StoryBody(
                state, onRefresh, onSourceSelected, onSectionSelected, mappingState,
                mappingActions, chapterState, chapterActions,
            )
        }
    }
}

@Composable
internal fun CompactStoryLayout(
    state: StoryUiState,
    story: StoryUiModel,
    readerTarget: ReaderTarget?,
    isResume: Boolean,
    downloadableReleaseId: ChapterReleaseId?,
    onRefresh: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    onSectionSelected: (StorySection) -> Unit,
    onLibraryStatusSelected: (LibraryStatus?) -> Unit,
    onRead: (ReaderTarget) -> Unit,
    onFindSource: () -> Unit,
    onDownload: (ChapterReleaseId) -> Unit,
    mappingState: MappingUiState?,
    mappingActions: MappingActions,
    chapterState: ChapterListUiState?,
    chapterActions: ChapterListActions,
    narrowHero: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        StoryHero(
            story, state.libraryStatus, readerTarget, isResume, downloadableReleaseId,
            onLibraryStatusSelected, onRead, onFindSource, onDownload, narrow = narrowHero,
        )
        StoryBody(
            state, onRefresh, onSourceSelected, onSectionSelected, mappingState,
            mappingActions, chapterState, chapterActions,
        )
    }
}

@Composable
private fun ColumnScope.StoryBody(
    state: StoryUiState,
    onRefresh: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    onSectionSelected: (StorySection) -> Unit,
    mappingState: MappingUiState?,
    mappingActions: MappingActions,
    chapterState: ChapterListUiState?,
    chapterActions: ChapterListActions,
) {
    StorySectionTabs(state.selectedSection, onSectionSelected)
    state.failure
        ?.takeIf { state.selectedSection.showsSourceDetailFailure() }
        ?.let { StoryFailureBanner(it, state.refreshing, onRefresh) }
    StorySectionContent(
        state, onRefresh, onSourceSelected, mappingState, mappingActions,
        chapterState, chapterActions, Modifier.weight(1f),
    )
}

private fun RowScope.storyPane(weight: Float, tag: String, traversal: Float): Modifier =
    Modifier.weight(weight).fillMaxSize().testTag(tag).semantics {
        isTraversalGroup = true
        traversalIndex = traversal
    }
