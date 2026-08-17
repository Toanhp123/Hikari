package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.ui.chapters.ChapterListActions
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariResponsiveContent
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
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
    val readableTargets = state.readableTargets
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
                    narrowHero = windowClass == HikariWindowClass.COMPACT,
                )
            }
        }
    }
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
