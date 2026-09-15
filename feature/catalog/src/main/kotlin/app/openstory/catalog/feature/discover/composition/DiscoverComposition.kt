package app.openstory.catalog.feature.discover.composition

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import app.openstory.artwork.runtime.ArtworkLoader
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.domain.read.StoryRoutePreview
import app.openstory.catalog.feature.artwork.LocalArtworkLoader
import app.openstory.catalog.feature.discover.DiscoverCardUi
import app.openstory.catalog.feature.discover.DiscoverContentState
import app.openstory.catalog.feature.discover.DiscoverSectionLabels
import app.openstory.catalog.feature.discover.DiscoverViewModel
import app.openstory.catalog.feature.discover.titleResource
import app.openstory.catalog.feature.discover.screen.DiscoverScreen
import app.openstory.catalog.feature.runtime.CatalogRuntimeAccess
import app.openstory.catalog.feature.runtime.CatalogRuntimeHolder

@Composable
internal fun DiscoverComposition(
    runtimeAccess: CatalogRuntimeAccess,
    artworkLoader: ArtworkLoader,
    mediaType: CatalogMediaType,
    onStorySelected: (StoryRouteArgs) -> Unit,
) {
    val discoverListState = rememberLazyListState()
    val discoverViewModel = rememberDiscoverViewModel(runtimeAccess.holder, mediaType)
    val discoverState by discoverViewModel.state.collectAsStateWithLifecycle()
    val trace = runtimeAccess.trace

    BindDiscoverLifecycle(discoverViewModel)

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val activeArtworkLoader = artworkLoader.takeIf { lifecycleState.isAtLeast(Lifecycle.State.STARTED) }
    LaunchedEffect(discoverState.content) {
        val content = discoverState.content as? DiscoverContentState.Content
        if (content?.sections?.any { it.cards.isNotEmpty() } == true) {
            trace.discoverContentReady()
        }
    }

    val sectionLabels = DiscoverSectionLabels(
        popular = stringResource(CatalogSectionKind.POPULAR.titleResource),
        latestUpdates = stringResource(CatalogSectionKind.LATEST_UPDATES.titleResource),
        topRated = stringResource(CatalogSectionKind.TOP_RATED.titleResource),
    )
    val selectStory = remember(mediaType, onStorySelected) {
        { card: DiscoverCardUi ->
            onStorySelected(
                StoryRouteArgs(
                    ref = card.ref,
                    originMediaContext = mediaType,
                    preview = StoryRoutePreview(
                        title = card.title,
                        coverLocator = card.coverLocator,
                        coverAssetKey = card.coverAssetKey,
                    ),
                ),
            )
        }
    }

    CompositionLocalProvider(LocalArtworkLoader provides activeArtworkLoader) {
        DiscoverScreen(
            mediaType = mediaType,
            state = discoverState,
            listState = discoverListState,
            sectionLabels = sectionLabels,
            onStorySelected = selectStory,
            onRefresh = discoverViewModel::refresh,
            onRetry = discoverViewModel::retry,
            onCoverReady = trace::discoverCoverReady,
        )
    }
}

@Composable
private fun BindDiscoverLifecycle(discoverViewModel: DiscoverViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, discoverViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> discoverViewModel.resume()
                Lifecycle.Event.ON_STOP -> discoverViewModel.quiesce()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            discoverViewModel.resume()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            discoverViewModel.quiesce()
        }
    }
}

@Composable
private fun rememberDiscoverViewModel(
    runtimeHolder: CatalogRuntimeHolder,
    mediaType: CatalogMediaType,
): DiscoverViewModel {
    val discoverFactory = remember(runtimeHolder, mediaType) {
        DiscoverViewModel.factory(mediaType) { runtimeHolder.discoverRuntime(mediaType) }
    }
    return viewModel(
        key = DiscoverViewModel.key(mediaType),
        factory = discoverFactory,
    )
}
