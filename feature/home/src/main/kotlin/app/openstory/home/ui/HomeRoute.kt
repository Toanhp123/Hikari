package app.openstory.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.openstory.model.StoryId

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onStorySelected: (StoryId) -> Unit,
    modifier: Modifier = Modifier,
    coverRenderer: HomeCoverRenderer = PlaceholderHomeCoverRenderer,
) {
    val state by viewModel.state.collectAsState()
    val selectedCatalog = state.selectedCatalog
    val actions = HomeActions(
        refresh = viewModel::refresh,
        storySelected = onStorySelected,
        catalogSelected = viewModel::selectCatalog,
        showCombined = viewModel::selectCombined,
    )

    if (state.selectedCatalogId != null && selectedCatalog != null) {
        CatalogHomeScreen(
            catalog = selectedCatalog,
            refreshing = state.refreshing,
            failure = state.refreshReport?.failed?.get(selectedCatalog.pluginId),
            actions = actions,
            modifier = modifier,
            coverRenderer = coverRenderer,
        )
    } else {
        HomeScreen(
            state = state,
            actions = actions,
            modifier = modifier,
            coverRenderer = coverRenderer,
        )
    }
}
