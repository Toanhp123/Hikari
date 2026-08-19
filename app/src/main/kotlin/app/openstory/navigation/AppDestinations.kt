package app.openstory.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.dashboard.HomeDashboardScreen
import app.openstory.catalog.ui.dashboard.HomeDashboardViewModel
import app.openstory.catalog.ui.discover.DiscoverScreen
import app.openstory.catalog.ui.discover.DiscoverViewModel
import app.openstory.catalog.ui.download.DownloadViewModel
import app.openstory.catalog.ui.downloads.DownloadsScreen
import app.openstory.catalog.ui.downloads.DownloadsViewModel
import app.openstory.catalog.ui.library.LibraryScreen
import app.openstory.catalog.ui.library.LibraryViewModel
import app.openstory.catalog.ui.search.SearchScreen
import app.openstory.catalog.ui.search.SearchViewModel
import app.openstory.catalog.ui.story.StoryAssistedArgs
import app.openstory.catalog.ui.story.StoryScreen
import app.openstory.catalog.ui.story.StoryViewModel
import app.openstory.catalog.ui.updates.UpdatesScreen
import app.openstory.catalog.ui.updates.UpdatesViewModel
import app.openstory.common.id.StoryId
import app.openstory.reader.ui.ReaderActions
import app.openstory.reader.ui.ReaderAssistedArgs
import app.openstory.reader.ui.ReaderScreen
import app.openstory.reader.ui.ReaderViewModel
import app.openstory.ui.HikariAppShellScope

@Composable
internal fun DownloadsDestination(
    contentPadding: PaddingValues,
    onStorySelected: (StoryId) -> Unit,
) {
    val viewModel = hiltViewModel<DownloadsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DownloadsScreen(
        state = state,
        onStorySelected = onStorySelected,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onRemove = viewModel::requestRemoval,
        onConfirmRemoval = viewModel::confirmRemoval,
        onDismissRemoval = viewModel::dismissRemoval,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun UpdatesDestination(
    contentPadding: PaddingValues,
    onStorySelected: (StoryId) -> Unit,
    onRead: (ReaderTarget) -> Unit,
) {
    val viewModel = hiltViewModel<UpdatesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    UpdatesScreen(state, onStorySelected, onRead, contentPadding = contentPadding)
}

@Composable
internal fun HomeDestination(
    contentPadding: PaddingValues,
    onDiscover: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onResume: (ReaderTarget) -> Unit,
    firstContentFocusRequester: FocusRequester,
    shellScope: HikariAppShellScope,
) {
    val viewModel = hiltViewModel<HomeDashboardViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeDashboardScreen(
        state = state,
        onDiscover = onDiscover,
        onStorySelected = onStorySelected,
        onResume = onResume,
        firstContentFocusRequester = firstContentFocusRequester,
        onUtilityRequested = shellScope.onUtilityRequested,
        utilityFocusRequester = shellScope.utilityFocusRequester,
        utilityNextFocusRequester = shellScope.utilityNextFocusRequester,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun DiscoverDestination(
    contentPadding: PaddingValues,
    onSearch: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    searchFocusRequester: FocusRequester? = null,
    searchNextFocusRequester: FocusRequester? = null,
    categoryFocusRequester: FocusRequester? = null,
    categoryNextFocusRequester: FocusRequester? = null,
    catalogFocusRequester: FocusRequester? = null,
    shellScope: HikariAppShellScope,
) {
    val viewModel = hiltViewModel<DiscoverViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiscoverScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onSearch = onSearch,
        onStorySelected = onStorySelected,
        onCatalogSelected = viewModel::selectCatalog,
        onCategorySelected = viewModel::selectCategory,
        onCombinedSelected = viewModel::selectCombined,
        searchFocusRequester = searchFocusRequester,
        searchNextFocusRequester = searchNextFocusRequester,
        categoryFocusRequester = categoryFocusRequester,
        categoryNextFocusRequester = categoryNextFocusRequester,
        catalogFocusRequester = catalogFocusRequester,
        onUtilityRequested = shellScope.onUtilityRequested,
        utilityFocusRequester = shellScope.utilityFocusRequester,
        utilityNextFocusRequester = shellScope.utilityNextFocusRequester,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun SearchDestination(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
) {
    val viewModel = hiltViewModel<SearchViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onRecentSelected = viewModel::selectRecent,
        onFilterValuesChange = viewModel::setFilterValues,
        onClearFilters = viewModel::clearFilters,
        onStorySelected = { story -> viewModel.selectStory(story, onStorySelected) },
        onBack = onBack,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun LibraryDestination(
    contentPadding: PaddingValues,
    onDiscover: () -> Unit,
    firstFilterFocusRequester: FocusRequester,
    shellScope: HikariAppShellScope,
    onStorySelected: (StoryId) -> Unit,
) {
    val viewModel = hiltViewModel<LibraryViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onStatusSelected = viewModel::selectStatus,
        onSourceFilterSelected = viewModel::selectSourceFilter,
        onSortSelected = viewModel::selectSort,
        onDisplayModeSelected = viewModel::selectDisplayMode,
        onClearFilters = viewModel::clearFilters,
        onResetFilters = viewModel::resetFilterSelections,
        onDiscover = onDiscover,
        onStorySelected = onStorySelected,
        firstFilterFocusRequester = firstFilterFocusRequester,
        onUtilityRequested = shellScope.onUtilityRequested,
        utilityFocusRequester = shellScope.utilityFocusRequester,
        utilityNextFocusRequester = shellScope.utilityNextFocusRequester,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun StoryDestination(
    route: AppRoute.Story,
    navigate: (AppRoute) -> Unit,
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    val storyId = StoryId(route.storyId)
    val viewModel = hiltViewModel<StoryViewModel, StoryViewModel.Factory>(
        creationCallback = { factory -> factory.create(StoryAssistedArgs(storyId)) },
    )
    val downloadViewModel = hiltViewModel<DownloadViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var prewarmSections by remember(storyId) { mutableStateOf(false) }
    LaunchedEffect(storyId, state.story != null) {
        if (state.story == null || prewarmSections) return@LaunchedEffect
        withFrameNanos { }
        prewarmSections = true
    }
    val navigateToReader: (ReaderTarget) -> Unit = { target -> navigate(target.readerRoute()) }
    val dependencies = storySectionDependencies(
        storyId = storyId,
        section = state.selectedSection,
        prewarmSections = prewarmSections,
        downloadViewModel = downloadViewModel,
        navigateToReader = navigateToReader,
        snackbarHostState = snackbarHostState,
    )
    StoryScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onSourceSelected = viewModel::selectSource,
        onSectionSelected = viewModel::selectSection,
        onLibraryStatusSelected = viewModel::changeLibraryStatus,
        onRead = navigateToReader,
        onDownload = downloadViewModel::download,
        mappingState = dependencies.mappingState,
        mappingActions = dependencies.mappingActions,
        chapterState = dependencies.chapterState,
        chapterActions = dependencies.chapterActions,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun ReaderDestination(
    route: AppRoute.Reader,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory>(
        creationCallback = { factory ->
            factory.create(ReaderAssistedArgs(route.storyId, route.chapterId, route.releaseId))
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReaderScreen(
        state,
        ReaderActions(
            onRetry = viewModel::retry,
            onReleaseSelected = viewModel::selectRelease,
            onPreviousChapter = viewModel::openChapter,
            onNextChapter = viewModel::openChapter,
            onIncreaseFont = viewModel::increaseFont,
            onDecreaseFont = viewModel::decreaseFont,
            onPositionChanged = viewModel::updatePosition,
            onFlushProgress = viewModel::flushProgress,
        ),
        onBack = onBack,
    )
}

private fun ReaderTarget.readerRoute() = AppRoute.Reader(
    storyId.value,
    chapterId.value,
    releaseId.value,
)
