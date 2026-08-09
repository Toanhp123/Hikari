package app.openstory.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.openstory.di.OpenStoryAppGraph
import app.openstory.home.ui.HomeRoute
import app.openstory.home.ui.HomeStorySelection
import app.openstory.home.ui.HomeViewModel
import app.openstory.home.ui.SearchScreen
import app.openstory.home.ui.SearchStorySelection
import app.openstory.home.ui.SearchViewModel
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.story.ui.StoryDetailRequest
import app.openstory.story.ui.StoryDetailScreen
import app.openstory.story.ui.StoryDetailViewModel

@Composable
fun OpenStoryNavDisplay(
    graph: OpenStoryAppGraph,
    viewModelStoreOwner: ViewModelStoreOwner,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(AppRoute.Home)
    val currentRoute = backStack.lastOrNull() as? AppRoute

    Scaffold(
        modifier = modifier,
        bottomBar = {
            OpenStoryBottomBar(
                currentRoute = currentRoute,
                onRouteSelected = { route ->
                    if (currentRoute != route) {
                        backStack.clear()
                        backStack.add(route)
                    }
                },
            )
        },
    ) { contentPadding ->
        NavDisplay(
            modifier = Modifier.padding(contentPadding),
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<AppRoute.Home> {
                    HomeDestination(
                        graph = graph,
                        viewModelStoreOwner = viewModelStoreOwner,
                        onSearch = { backStack.add(AppRoute.Search) },
                        onStorySelected = { backStack.add(it.toStoryRoute()) },
                    )
                }
                entry<AppRoute.Search> {
                    SearchDestination(
                        graph = graph,
                        viewModelStoreOwner = viewModelStoreOwner,
                        onStorySelected = { backStack.add(it.toStoryRoute()) },
                    )
                }
                entry<AppRoute.Library> { PlaceholderDestination("Library") }
                entry<AppRoute.Plugins> { PlaceholderDestination("Plugins") }
                entry<AppRoute.Settings> { PlaceholderDestination("Settings") }
                entry<AppRoute.Story> { route ->
                    StoryDestination(
                        graph = graph,
                        viewModelStoreOwner = viewModelStoreOwner,
                        route = route,
                    )
                }
                entry<AppRoute.Reader> { PlaceholderDestination("Reader") }
            },
        )
    }
}

@Composable
private fun OpenStoryBottomBar(
    currentRoute: AppRoute?,
    onRouteSelected: (AppRoute) -> Unit,
) {
    NavigationBar {
        topLevelRoutes.forEach { route ->
            val label = route.topLevelLabel()
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { onRouteSelected(route) },
                icon = { Text(label.take(1)) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun HomeDestination(
    graph: OpenStoryAppGraph,
    viewModelStoreOwner: ViewModelStoreOwner,
    onSearch: () -> Unit,
    onStorySelected: (HomeStorySelection) -> Unit,
) {
    val viewModel = appViewModel(
        owner = viewModelStoreOwner,
        key = HOME_VIEW_MODEL_KEY,
        modelClass = HomeViewModel::class.java,
        create = graph::createHomeViewModel,
    )
    HomeRoute(
        viewModel = viewModel,
        onSearch = onSearch,
        onStorySelected = onStorySelected,
    )
}

@Composable
private fun SearchDestination(
    graph: OpenStoryAppGraph,
    viewModelStoreOwner: ViewModelStoreOwner,
    onStorySelected: (SearchStorySelection) -> Unit,
) {
    val viewModel = appViewModel(
        owner = viewModelStoreOwner,
        key = SEARCH_VIEW_MODEL_KEY,
        modelClass = SearchViewModel::class.java,
        create = graph::createSearchViewModel,
    )
    val state by viewModel.state.collectAsState()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onFilterValuesChange = viewModel::setFilterValues,
        onStoryClick = onStorySelected,
    )
}

@Composable
private fun StoryDestination(
    graph: OpenStoryAppGraph,
    viewModelStoreOwner: ViewModelStoreOwner,
    route: AppRoute.Story,
) {
    val viewModel = appViewModel(
        owner = viewModelStoreOwner,
        key = route.storyViewModelKey(),
        modelClass = StoryDetailViewModel::class.java,
        create = {
            graph.createStoryDetailViewModel(
                StoryDetailRequest(
                    storyId = StoryId(route.storyId),
                    pluginId = PluginId(route.pluginId),
                    sourceId = route.sourceId,
                ),
            )
        },
    )
    val state by viewModel.state.collectAsState()
    StoryDetailScreen(
        state = state,
        onRetry = viewModel::refresh,
    )
}

private fun HomeStorySelection.toStoryRoute(): AppRoute.Story = AppRoute.Story(
    storyId = storyId.value,
    pluginId = pluginId.value,
    sourceId = sourceId,
)

private fun SearchStorySelection.toStoryRoute(): AppRoute.Story = AppRoute.Story(
    storyId = storyId.value,
    pluginId = pluginId.value,
    sourceId = sourceId,
)

private fun AppRoute.Story.storyViewModelKey(): String =
    "story:$storyId:$pluginId:$sourceId"

private fun AppRoute.topLevelLabel(): String = when (this) {
    AppRoute.Home -> "Home"
    AppRoute.Library -> "Library"
    AppRoute.Plugins -> "Plugins"
    else -> error("Route $this is not a top-level destination")
}

@Composable
private fun PlaceholderDestination(
    title: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title)
    }
}

private const val HOME_VIEW_MODEL_KEY = "wave05-home"
private const val SEARCH_VIEW_MODEL_KEY = "wave05-search"
