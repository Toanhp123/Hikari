package app.openstory.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.openstory.common.id.StoryId

@Composable
fun AppNavHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = { AppBottomBar(navigator.currentRoute, navigator::selectTopLevel) },
    ) { contentPadding ->
        NavDisplay(
            modifier = Modifier.padding(contentPadding),
            backStack = navigator.backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            onBack = navigator::back,
            entryProvider = entryProvider {
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
                entry<AppRoute.Plugins> { PlaceholderDestination("Plugins") }
                entry<AppRoute.Settings> { PlaceholderDestination("Settings") }
                entry<AppRoute.Story> { route -> StoryDestination(route) }
                entry<AppRoute.Reader> { PlaceholderDestination("Reader") }
            },
        )
    }
}

@Composable
private fun AppBottomBar(
    currentRoute: AppRoute?,
    onSelected: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        topLevelDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onSelected(destination) },
                icon = { Text(destination.label.take(1)) },
                label = { Text(destination.label) },
            )
        }
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
    )
}

@Composable
private fun StoryDestination(route: AppRoute.Story) {
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mappingState by mappingViewModel.state.collectAsStateWithLifecycle()
    val chapterState by chapterViewModel.state.collectAsStateWithLifecycle()
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
        ),
    )
}

@Composable
private fun PlaceholderDestination(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(title)
    }
}
