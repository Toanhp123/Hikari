package app.openstory.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.openstory.catalog.ui.discover.DiscoverScreen
import app.openstory.catalog.ui.discover.DiscoverViewModel
import app.openstory.catalog.ui.dashboard.HomeDashboardScreen
import app.openstory.catalog.ui.dashboard.HomeDashboardViewModel
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
import app.openstory.catalog.ui.downloads.DownloadsScreen
import app.openstory.catalog.ui.downloads.DownloadsViewModel
import app.openstory.catalog.ui.updates.UpdatesScreen
import app.openstory.catalog.ui.updates.UpdatesViewModel
import app.openstory.common.id.StoryId
import app.openstory.designsystem.feedback.HikariSnackbarHost
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.ui.HikariAppShell
import app.openstory.ui.HikariAppShellScope
import app.openstory.ui.HikariUtilitySheet
import app.openstory.reader.ui.ReaderActions
import app.openstory.reader.ui.ReaderAssistedArgs
import app.openstory.reader.ui.ReaderScreen
import app.openstory.reader.ui.ReaderViewModel
import androidx.compose.material3.MaterialTheme

@Composable
fun AppNavHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val discoverSearchFocus = remember { FocusRequester() }
    val utilityFocus = remember { FocusRequester() }
    val discoverCategoryFocus = remember { FocusRequester() }
    val discoverCatalogFocus = remember { FocusRequester() }
    val homeContentFocus = remember { FocusRequester() }
    val libraryFilterFocus = remember { FocusRequester() }
    var showUtilitySheet by remember { mutableStateOf(false) }
    val focus = AppNavFocus(
        discoverSearchFocus,
        utilityFocus,
        discoverCategoryFocus,
        discoverCatalogFocus,
        homeContentFocus,
        libraryFilterFocus,
    )
    HikariAppShell(
        currentRoute = navigator.currentRoute,
        onTopLevelSelected = navigator::selectTopLevel,
        onUtilityRequested = { showUtilitySheet = true },
        utilityFocusRequester = utilityFocus,
        utilityNextFocusRequester = focus.utilityNext(navigator.currentRoute),
        modifier = modifier,
    ) { contentPadding ->
        AppNavigationContent(navigator, focus, snackbarHostState, contentPadding, this)
    }
    if (showUtilitySheet) {
        HikariUtilitySheet(
            onDismiss = { showUtilitySheet = false },
            onDestinationSelected = { route ->
                showUtilitySheet = false
                navigator.navigate(route)
            },
        )
    }
}

@Composable
private fun AppNavigationContent(
    navigator: AppNavigator,
    focus: AppNavFocus,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    shellScope: HikariAppShellScope,
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
                    DiscoverDestination(
                        contentPadding = contentPadding,
                        onSearch = { navigator.navigate(AppRoute.Search) },
                        onStorySelected = { storyId ->
                            navigator.navigate(AppRoute.Story(storyId.value))
                        },
                        searchFocusRequester = focus.discoverSearch,
                        searchNextFocusRequester = focus.utility,
                        categoryFocusRequester = focus.discoverCategory,
                        categoryNextFocusRequester = focus.discoverCatalog,
                        catalogFocusRequester = focus.discoverCatalog,
                        shellScope = shellScope,
                    )
                }
                entry<AppRoute.Home> {
                    HomeDestination(
                        contentPadding = contentPadding,
                        onDiscover = { navigator.selectTopLevel(TopLevelDestination.Discover) },
                        onStorySelected = { storyId ->
                            navigator.navigate(AppRoute.Story(storyId.value))
                        },
                        onResume = { target ->
                            navigator.navigate(
                                AppRoute.Reader(
                                    target.storyId.value,
                                    target.chapterId.value,
                                    target.releaseId.value,
                                ),
                            )
                        },
                        firstContentFocusRequester = focus.homeContent,
                        shellScope = shellScope,
                    )
                }
                entry<AppRoute.Search> {
                    SearchDestination(contentPadding, navigator::back) { storyId ->
                        navigator.navigate(AppRoute.Story(storyId.value))
                    }
                }
                entry<AppRoute.Library> {
                    LibraryDestination(
                        contentPadding = contentPadding,
                        onDiscover = { navigator.selectTopLevel(TopLevelDestination.Discover) },
                        firstFilterFocusRequester = focus.libraryFilter,
                        shellScope = shellScope,
                    ) { storyId ->
                        navigator.navigate(AppRoute.Story(storyId.value))
                    }
                }
                entry<AppRoute.Downloads> {
                    DownloadsDestination(
                        contentPadding = contentPadding,
                        onStorySelected = { navigator.navigate(AppRoute.Story(it.value)) },
                    )
                }
                entry<AppRoute.Updates> {
                    UpdatesDestination(
                        contentPadding = contentPadding,
                        onStorySelected = { navigator.navigate(AppRoute.Story(it.value)) },
                        onRead = { target ->
                            navigator.navigate(
                                AppRoute.Reader(
                                    target.storyId.value,
                                    target.chapterId.value,
                                    target.releaseId.value,
                                ),
                            )
                        },
                    )
                }
                entry<AppRoute.Story> { route ->
                    StoryDestination(route, navigator::navigate, contentPadding)
                }
                entry<AppRoute.Reader> { route ->
                    ReaderDestination(route, navigator::navigate, navigator::back)
                }
            },
        )
        HikariSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (shouldShowFloatingNavigation(navigator.currentRoute)) {
                        Modifier.navigationBarsPadding().padding(
                            bottom = MaterialTheme.hikariDimensions.floatingNavigationClearance,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

private data class AppNavFocus(
    val discoverSearch: FocusRequester,
    val utility: FocusRequester,
    val discoverCategory: FocusRequester,
    val discoverCatalog: FocusRequester,
    val homeContent: FocusRequester,
    val libraryFilter: FocusRequester,
) {
    fun utilityNext(route: AppRoute?): FocusRequester = when (route) {
        AppRoute.Home -> homeContent
        AppRoute.Library -> libraryFilter
        else -> discoverCategory
    }
}

@Composable
private fun DownloadsDestination(
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
private fun UpdatesDestination(
    contentPadding: PaddingValues,
    onStorySelected: (StoryId) -> Unit,
    onRead: (app.openstory.catalog.ui.components.ReaderTarget) -> Unit,
) {
    val viewModel = hiltViewModel<UpdatesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    UpdatesScreen(state, onStorySelected, onRead, contentPadding = contentPadding)
}

@Composable
private fun HomeDestination(
    contentPadding: PaddingValues,
    onDiscover: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onResume: (app.openstory.catalog.ui.components.ReaderTarget) -> Unit,
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
private fun DiscoverDestination(
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
private fun SearchDestination(
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
private fun LibraryDestination(
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
private fun StoryDestination(
    route: AppRoute.Story,
    navigate: (AppRoute) -> Unit,
    contentPadding: PaddingValues,
) {
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
    val navigateToReader: (app.openstory.catalog.ui.components.ReaderTarget) -> Unit = { target ->
        navigate(target.readerRoute())
    }
    StoryScreen(
        state = state,
        onRetry = viewModel::retry,
        onSourceSelected = viewModel::selectSource,
        onSectionSelected = viewModel::selectSection,
        onLibraryStatusSelected = viewModel::changeLibraryStatus,
        onRead = navigateToReader,
        onDownload = downloadViewModel::download,
        mappingState = mappingState,
        mappingActions = mappingViewModel.actions(),
        chapterState = chapterState,
        chapterActions = ChapterListActions(
            onToggleExpanded = chapterViewModel::toggleExpanded,
            onFilterSelected = chapterViewModel::selectFilter,
            onTombstonesVisible = chapterViewModel::setTombstonesVisible,
            onKeepGrouped = chapterViewModel::keepGrouped,
            onSeparate = chapterViewModel::separate,
            onRead = navigateToReader,
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
        contentPadding = contentPadding,
    )
}

@Composable
private fun ReaderDestination(
    route: AppRoute.Reader,
    navigate: (AppRoute) -> Unit,
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
        onBack = onBack,
    )
}

private fun MappingViewModel.actions() = MappingActions(
    onSearch = ::search,
    onUrlChange = ::updateUrl,
    onResolveUrl = ::resolveUrl,
    onApprove = ::approve,
    onReject = ::reject,
)

private fun app.openstory.catalog.ui.components.ReaderTarget.readerRoute() = AppRoute.Reader(
    storyId.value,
    chapterId.value,
    releaseId.value,
)

