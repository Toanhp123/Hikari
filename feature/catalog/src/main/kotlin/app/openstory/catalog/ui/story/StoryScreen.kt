package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import app.openstory.catalog.ui.chapters.ChapterList
import app.openstory.catalog.ui.chapters.ChapterListActions
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariResponsiveContent
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariLayoutRatios
import app.openstory.library.LibraryStatus

@Composable
fun StoryScreen(
    state: StoryUiState,
    onRefresh: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    onSectionSelected: (StorySection) -> Unit = {},
    onLibraryStatusSelected: (LibraryStatus?) -> Unit = {},
    onRead: (ReaderTarget) -> Unit = {},
    onDownload: (ChapterReleaseId) -> Unit = {},
    mappingState: MappingUiState? = null,
    mappingActions: MappingActions = MappingActions(),
    chapterState: ChapterListUiState? = null,
    chapterActions: ChapterListActions = ChapterListActions(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val story = state.story
    if (story == null) {
        HikariDestinationScaffold(modifier) {
            if (state.refreshing && state.failure == null) {
                HikariLoadingState("Loading story", Modifier.fillMaxSize().padding(contentPadding))
            } else {
                HikariPullToRefresh(
                    refreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .testTag("story-empty-pull-refresh"),
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Box(Modifier.fillParentMaxSize()) {
                                EmptyStory(state, onRefresh, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
        return
    }
    val readableTargets = chapterState?.readableTargets.orEmpty()
    val validatedResumeTarget = state.resumeTarget?.takeIf { target ->
        readableTargets.any { it.chapterId == target.chapterId && it.releaseId == target.releaseId }
    }
    val firstReadableTarget = readableTargets.firstOrNull()
    val readerTarget = validatedResumeTarget ?: firstReadableTarget
    HikariDestinationScaffold(modifier) {
        HikariResponsiveContent(Modifier.fillMaxSize().padding(contentPadding)) {
            if (windowClass == HikariWindowClass.MEDIUM) {
                MediumStoryLayout(
                    state, story, readerTarget, validatedResumeTarget != null,
                    firstReadableTarget?.releaseId, onRefresh, onSourceSelected, onSectionSelected,
                    onLibraryStatusSelected, onRead, onDownload, mappingState, mappingActions,
                    chapterState, chapterActions,
                )
            } else {
                CompactStoryLayout(
                    state, story, readerTarget, validatedResumeTarget != null,
                    firstReadableTarget?.releaseId, onRefresh, onSourceSelected, onSectionSelected,
                    onLibraryStatusSelected, onRead, onDownload, mappingState, mappingActions,
                    chapterState, chapterActions,
                )
            }
        }
    }
}

@Composable
private fun MediumStoryLayout(
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
                onLibraryStatusSelected, onRead, onDownload, narrow = true,
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
private fun CompactStoryLayout(
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
    onDownload: (ChapterReleaseId) -> Unit,
    mappingState: MappingUiState?,
    mappingActions: MappingActions,
    chapterState: ChapterListUiState?,
    chapterActions: ChapterListActions,
) {
    Column(Modifier.fillMaxSize()) {
        StoryHero(
            story, state.libraryStatus, readerTarget, isResume, downloadableReleaseId,
            onLibraryStatusSelected, onRead, onDownload,
        )
        StoryBody(
            state, onRefresh, onSourceSelected, onSectionSelected, mappingState,
            mappingActions, chapterState, chapterActions,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StoryBody(
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

@Composable
private fun StorySectionTabs(selectedSection: StorySection, onSelected: (StorySection) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selectedSection.ordinal) {
        StorySection.entries.forEach { section ->
            val selected = section == selectedSection
            Tab(
                selected = selected,
                onClick = { onSelected(section) },
                modifier = Modifier
                    .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
                    .testTag("story-tab-${section.name.lowercase()}")
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                        stateDescription = if (selected) "Active section" else "Inactive section"
                    },
                text = { Text(section.label()) },
            )
        }
    }
}

@Composable
private fun StorySectionContent(
    state: StoryUiState,
    onRefresh: () -> Unit,
    onSourceSelected: (PluginId, String) -> Unit,
    mappingState: MappingUiState?,
    mappingActions: MappingActions,
    chapterState: ChapterListUiState?,
    chapterActions: ChapterListActions,
    modifier: Modifier,
) {
    when (state.selectedSection) {
        StorySection.OVERVIEW -> StoryOverview(
            story = requireNotNull(state.story),
            modifier = modifier,
            refreshing = state.refreshing,
            onRefresh = onRefresh,
        )
        StorySection.CHAPTERS -> ChapterList(
            state = chapterState ?: ChapterListUiState(state.storyId),
            actions = chapterActions,
            modifier = modifier,
            contentPadding = storySectionContentPadding(),
        )
        StorySection.SOURCES -> StorySources(
            story = requireNotNull(state.story),
            selectedSource = state.selectedSource,
            refreshing = state.refreshing,
            onRefresh = onRefresh,
            onSourceSelected = onSourceSelected,
            mappingState = mappingState,
            mappingActions = mappingActions,
            modifier = modifier,
        )
    }
}

@Composable
private fun StoryFailureBanner(failure: StoryRefreshFailure, refreshing: Boolean, onRefresh: () -> Unit) {
    HikariInlineFeedback(
        message = "Source detail refresh failed: ${failure.code}",
        actionLabel = if (failure.retryable) "Retry" else null,
        actionEnabled = !refreshing,
        onAction = if (failure.retryable) onRefresh else null,
        actionModifier = Modifier.testTag("story-retry"),
    )
}

@Composable
private fun EmptyStory(state: StoryUiState, onRefresh: () -> Unit, modifier: Modifier) {
    val retryable = state.failure?.retryable == true
    HikariErrorState(
        title = "Story unavailable",
        message = state.failure?.let { "Source detail refresh failed: ${it.code}" },
        actionLabel = if (retryable) "Retry" else null,
        onAction = if (retryable) onRefresh else null,
        modifier = modifier.fillMaxSize(),
    )
}

private fun StorySection.showsSourceDetailFailure(): Boolean = this != StorySection.CHAPTERS

private fun StorySection.label() = when (this) {
    StorySection.OVERVIEW -> "Overview"
    StorySection.CHAPTERS -> "Chapters"
    StorySection.SOURCES -> "Sources"
}
