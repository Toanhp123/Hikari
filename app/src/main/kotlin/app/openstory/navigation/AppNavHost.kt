package app.openstory.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.openstory.catalog.ui.home.HomeScreen
import app.openstory.catalog.ui.home.HomeViewModel
import app.openstory.catalog.ui.chapters.ChapterListActions
import app.openstory.catalog.ui.chapters.ChapterListAssistedArgs
import app.openstory.catalog.ui.chapters.ChapterListViewModel
import app.openstory.catalog.ui.library.LibraryScreen
import app.openstory.catalog.ui.library.LibraryViewModel
import app.openstory.catalog.ui.mapping.MappingActions
import app.openstory.catalog.ui.mapping.MappingAssistedArgs
import app.openstory.catalog.ui.mapping.MappingViewModel
import app.openstory.catalog.ui.search.SearchScreen
import app.openstory.catalog.ui.search.SearchViewModel
import app.openstory.catalog.ui.story.StoryAssistedArgs
import app.openstory.catalog.ui.story.StoryScreen
import app.openstory.catalog.ui.story.StoryViewModel
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.catalog.ui.download.DownloadViewModel
import app.openstory.common.id.StoryId
import app.openstory.designsystem.feedback.HikariSnackbarHost
import app.openstory.ui.HikariAppShell
import app.openstory.ui.HikariUtilitySheet
import app.openstory.ui.hikariTopLevelContentPadding
import app.openstory.reader.ui.ReaderActions
import app.openstory.reader.ui.ReaderAssistedArgs
import app.openstory.reader.ui.ReaderScreen
import app.openstory.reader.ui.ReaderViewModel

@Composable
fun AppNavHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showUtilitySheet by remember { mutableStateOf(false) }
    HikariAppShell(
        currentRoute = navigator.currentRoute,
        onTopLevelSelected = navigator::selectTopLevel,
        onUtilityRequested = { showUtilitySheet = true },
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                modifier = Modifier.fillMaxSize(),
                backStack = navigator.backStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                onBack = navigator::back,
                entryProvider = entryProvider {
                entry<AppRoute.Discover> {
                    HomeDestination(
                        onSearch = { navigator.navigate(AppRoute.Search) },
                        onStorySelected = { storyId ->
                            navigator.navigate(AppRoute.Story(storyId.value))
                        },
                    )
                }
                entry<AppRoute.Home> {
                    HomeDestination(
                        onSearch = { navigator.navigate(AppRoute.Search) },
                        onStorySelected = { storyId ->
                            navigator.navigate(AppRoute.Story(storyId.value))
                        },
                    )
                }
                entry<AppRoute.Search> {
                    SearchDestination { storyId ->
                        navigator.navigate(AppRoute.Story(storyId.value))
                    }
                }
                entry<AppRoute.Library> {
                    LibraryDestination { storyId ->
                        navigator.navigate(AppRoute.Story(storyId.value))
                    }
                }
                entry<AppRoute.Story> { route -> StoryDestination(route, navigator::navigate) }
                entry<AppRoute.Reader> { route -> ReaderDestination(route, navigator::navigate) }
                },
            )
            HikariSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (shouldShowFloatingNavigation(navigator.currentRoute)) {
                            Modifier.navigationBarsPadding().padding(bottom = 92.dp)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
    if (showUtilitySheet) {
        HikariUtilitySheet(
            onDismiss = { showUtilitySheet = false },
        )
    }
}

@Composable
private fun HomeDestination(
    onSearch: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
) {
    val viewModel = hiltViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onSearch = onSearch,
        onStorySelected = onStorySelected,
        onCatalogSelected = viewModel::selectCatalog,
        onCombinedSelected = viewModel::selectCombined,
        modifier = Modifier.hikariTopLevelContentPadding(),
    )
}

@Composable
private fun SearchDestination(onStorySelected: (StoryId) -> Unit) {
    val viewModel = hiltViewModel<SearchViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onRecentSelected = viewModel::selectRecent,
        onFilterValuesChange = viewModel::setFilterValues,
        onClearFilters = viewModel::clearFilters,
        onStorySelected = { story -> viewModel.selectStory(story, onStorySelected) },
    )
}

@Composable
private fun LibraryDestination(onStorySelected: (StoryId) -> Unit) {
    val viewModel = hiltViewModel<LibraryViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        onStatusSelected = viewModel::selectStatus,
        onSortSelected = viewModel::selectSort,
        onStorySelected = onStorySelected,
        modifier = Modifier.hikariTopLevelContentPadding(),
    )
}

@Composable
private fun StoryDestination(route: AppRoute.Story, navigate: (AppRoute) -> Unit) {
    val storyId = StoryId(route.storyId)
    val viewModel = hiltViewModel<StoryViewModel, StoryViewModel.Factory>(
        creationCallback = { factory -> factory.create(StoryAssistedArgs(storyId)) },
    )
    val mappingViewModel = hiltViewModel<MappingViewModel, MappingViewModel.Factory>(
        creationCallback = { factory -> factory.create(MappingAssistedArgs(storyId)) },
    )
    val chapterViewModel = hiltViewModel<ChapterListViewModel, ChapterListViewModel.Factory>(
        creationCallback = { factory -> factory.create(ChapterListAssistedArgs(storyId)) },
    )
    val downloadViewModel = hiltViewModel<DownloadViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mappingState by mappingViewModel.state.collectAsStateWithLifecycle()
    val chapterState by chapterViewModel.state.collectAsStateWithLifecycle()
    val downloadState by downloadViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(chapterState.chapters) {
        chapterState.chapters.flatMap { it.releases }.forEach { downloadViewModel.watch(it.id) }
    }
    StoryScreen(
        state = state,
        onRetry = viewModel::retry,
        onSourceSelected = viewModel::selectSource,
        mappingState = mappingState,
        mappingActions = MappingActions(
            onSearch = mappingViewModel::search,
            onUrlChange = mappingViewModel::updateUrl,
            onResolveUrl = mappingViewModel::resolveUrl,
            onApprove = mappingViewModel::approve,
            onReject = mappingViewModel::reject,
        ),
        chapterState = chapterState,
        chapterActions = ChapterListActions(
            onToggleExpanded = chapterViewModel::toggleExpanded,
            onFilterSelected = chapterViewModel::selectFilter,
            onTombstonesVisible = chapterViewModel::setTombstonesVisible,
            onKeepGrouped = chapterViewModel::keepGrouped,
            onSeparate = chapterViewModel::separate,
            onRead = { chapterId, releaseId ->
                navigate(AppRoute.Reader(storyId.value, chapterId.value, releaseId.value))
            },
            onDownloadRange = downloadViewModel::downloadRange,
            onDownloadFiltered = downloadViewModel::downloadFiltered,
            downloadState = downloadState::status,
            pendingRemoval = downloadState.pendingRemoval,
            downloadActions = DownloadActions(
                onDownload = downloadViewModel::download,
                onCancel = downloadViewModel::cancel,
                onRetry = downloadViewModel::retry,
                onRemove = downloadViewModel::requestRemoval,
                onConfirmRemoval = downloadViewModel::confirmRemoval,
                onDismissRemoval = downloadViewModel::dismissRemoval,
            ),
        ),
    )
}

@Composable
private fun ReaderDestination(route: AppRoute.Reader, navigate: (AppRoute) -> Unit) {
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
            onPreviousChapter = { chapterId ->
                navigate(AppRoute.Reader(route.storyId, chapterId.value, null))
            },
            onNextChapter = { chapterId ->
                navigate(AppRoute.Reader(route.storyId, chapterId.value, null))
            },
            onIncreaseFont = viewModel::increaseFont,
            onDecreaseFont = viewModel::decreaseFont,
            onPositionChanged = viewModel::updatePosition,
            onFlushProgress = viewModel::flushProgress,
        ),
    )
}

