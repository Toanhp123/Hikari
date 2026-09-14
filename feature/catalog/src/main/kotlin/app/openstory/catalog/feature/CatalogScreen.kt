package app.openstory.catalog.feature

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.feature.discover.DiscoverCardUi
import app.openstory.catalog.feature.discover.DiscoverScreen
import app.openstory.catalog.feature.discover.DiscoverSectionLabels
import app.openstory.catalog.feature.discover.DiscoverUiState

@Composable
internal fun CatalogScreen(
    mediaType: CatalogMediaType,
    discoverListState: LazyListState,
    discoverState: DiscoverUiState?,
    actions: CatalogScreenActions,
    onDiscoverCoverReady: () -> Unit = {},
) {
    discoverState?.let { state ->
        DiscoverScreen(
            mediaType = mediaType,
            state = state,
            listState = discoverListState,
            sectionLabels = DiscoverSectionLabels(
                popular = stringResource(CatalogSectionKind.POPULAR.titleResource),
                latestUpdates = stringResource(CatalogSectionKind.LATEST_UPDATES.titleResource),
                topRated = stringResource(CatalogSectionKind.TOP_RATED.titleResource),
            ),
            onStorySelected = actions.onStorySelected,
            onRefresh = actions.onDiscoverRefresh,
            onRetry = actions.onDiscoverRetry,
            onCoverReady = onDiscoverCoverReady,
        )
    } ?: CatalogLoading()
}

internal data class CatalogScreenActions(
    val onStorySelected: (DiscoverCardUi) -> Unit,
    val onDiscoverRefresh: () -> Unit,
    val onDiscoverRetry: () -> Unit,
)

@Composable
private fun CatalogLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
