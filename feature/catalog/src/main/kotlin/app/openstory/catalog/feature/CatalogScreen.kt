package app.openstory.catalog.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.discover.DiscoverScreen
import app.openstory.catalog.feature.discover.DiscoverUiState
import app.openstory.catalog.feature.story.StoryDetailScreen
import app.openstory.catalog.feature.story.StoryDetailUiState

@Composable
internal fun CatalogScreen(
    route: CatalogRoute,
    discoverListState: LazyListState,
    discoverState: DiscoverUiState?,
    storyState: StoryDetailUiState?,
    actions: CatalogScreenActions,
) {
    when (route) {
        CatalogRoute.Discover -> discoverState?.let { state ->
            DiscoverScreen(
                state = state,
                listState = discoverListState,
                onMediaSelected = actions.onMediaSelected,
                onStorySelected = actions.onStorySelected,
                onRetry = actions.onDiscoverRetry,
            )
        } ?: CatalogRouteLoading()
        is CatalogRoute.Story -> {
            BackHandler(onBack = actions.onBack)
            if (storyState?.destinationActive == true && storyState.ref == route.ref) {
                StoryDetailScreen(storyState, actions.onBack, actions.onStoryRetry)
            } else {
                CatalogRouteLoading()
            }
        }
    }
}

internal data class CatalogScreenActions(
    val onMediaSelected: (CatalogMediaType) -> Unit,
    val onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
    val onDiscoverRetry: () -> Unit,
    val onStoryRetry: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
private fun CatalogRouteLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
