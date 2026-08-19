package app.openstory.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openstory.catalog.ui.chapters.ChapterListActions
import app.openstory.catalog.ui.chapters.ChapterListAssistedArgs
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.chapters.ChapterListViewModel
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.catalog.ui.download.DownloadViewModel
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingEvent
import app.openstory.catalog.ui.mapping.MappingAssistedArgs
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.catalog.ui.mapping.MappingViewModel
import app.openstory.catalog.ui.story.StorySection
import app.openstory.common.id.StoryId

internal data class StorySectionDependencies(
    val mappingState: MappingUiState? = null,
    val mappingActions: MappingActions = MappingActions(),
    val chapterState: ChapterListUiState? = null,
    val chapterActions: ChapterListActions = ChapterListActions(),
)

@Composable
internal fun storySectionDependencies(
    storyId: StoryId,
    section: StorySection,
    prewarmSections: Boolean,
    downloadViewModel: DownloadViewModel,
    navigateToReader: (ReaderTarget) -> Unit,
    snackbarHostState: SnackbarHostState,
): StorySectionDependencies {
    val source = if (prewarmSections || section == StorySection.SOURCES) {
        sourceDependencies(storyId, snackbarHostState)
    } else {
        StorySectionDependencies()
    }
    val chapters = if (prewarmSections || section == StorySection.CHAPTERS) {
        chapterDependencies(storyId, downloadViewModel, navigateToReader)
    } else {
        StorySectionDependencies()
    }
    return StorySectionDependencies(
        mappingState = source.mappingState,
        mappingActions = source.mappingActions,
        chapterState = chapters.chapterState,
        chapterActions = chapters.chapterActions,
    )
}

@Composable
private fun sourceDependencies(
    storyId: StoryId,
    snackbarHostState: SnackbarHostState,
): StorySectionDependencies {
    val viewModel = hiltViewModel<MappingViewModel, MappingViewModel.Factory>(
        creationCallback = { factory -> factory.create(MappingAssistedArgs(storyId)) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event -> snackbarHostState.showSnackbar(event.message()) }
    }
    return StorySectionDependencies(
        mappingState = state,
        mappingActions = viewModel.actions(),
    )
}

@Composable
private fun chapterDependencies(
    storyId: StoryId,
    downloadViewModel: DownloadViewModel,
    navigateToReader: (ReaderTarget) -> Unit,
): StorySectionDependencies {
    val viewModel = hiltViewModel<ChapterListViewModel, ChapterListViewModel.Factory>(
        creationCallback = { factory -> factory.create(ChapterListAssistedArgs(storyId)) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloadState by downloadViewModel.state.collectAsStateWithLifecycle()
    val statuses by downloadViewModel.statuses.collectAsStateWithLifecycle(initialValue = emptyMap())
    return StorySectionDependencies(
        chapterState = state,
        chapterActions = ChapterListActions(
            onToggleExpanded = viewModel::toggleExpanded,
            onFilterSelected = viewModel::selectFilter,
            onTombstonesVisible = viewModel::setTombstonesVisible,
            onKeepGrouped = viewModel::keepGrouped,
            onSeparate = viewModel::separate,
            onRead = navigateToReader,
            onDownloadRange = downloadViewModel::downloadRange,
            onDownloadFiltered = downloadViewModel::downloadFiltered,
            downloadState = { releaseId -> statuses[releaseId] },
            pendingRemoval = downloadState.pendingRemoval,
            downloadActions = downloadViewModel.actions(),
        ),
    )
}

private fun DownloadViewModel.actions() = DownloadActions(
    onDownload = ::download,
    onCancel = ::cancel,
    onRetry = ::retry,
    onRemove = ::requestRemoval,
    onConfirmRemoval = ::confirmRemoval,
    onDismissRemoval = ::dismissRemoval,
)

private fun MappingViewModel.actions() = MappingActions(
    onSearch = ::search,
    onUrlChange = ::updateUrl,
    onResolveUrl = ::resolveUrl,
    onApprove = ::approve,
    onReject = ::reject,
)

private fun MappingEvent.message(): String = when (this) {
    MappingEvent.SOURCE_LINKED -> "Reading source linked"
    MappingEvent.SOURCE_REPLACED -> "Reading source replaced"
    MappingEvent.SOURCE_ALREADY_LINKED -> "Reading source already linked"
}
