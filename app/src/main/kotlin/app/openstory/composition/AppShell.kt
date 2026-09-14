package app.openstory.composition

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.domain.read.StoryRoutePreview
import app.openstory.catalog.feature.CatalogCoverArtwork
import app.openstory.catalog.feature.rememberCatalogArtworkLoader
import app.openstory.catalog.feature.rememberCatalogRuntimeAccess
import app.openstory.composition.navigation.StoryRouteCodec
import app.openstory.execution.ProcessWorkAdmissionOwner
import app.openstory.library.feature.HomeEntryPoint
import app.openstory.navigation.AppFocusedDestination
import app.openstory.navigation.AppNavHost
import app.openstory.navigation.AppRoute
import app.openstory.navigation.newStoryRouteEntryId
import app.openstory.navigation.rememberAppNavigationState

@Composable
internal fun AppShell() {
    val navigationState = rememberAppNavigationState()
    val runtimeAccess = rememberCatalogRuntimeAccess()
    val processWorkAdmission =
        (LocalContext.current.applicationContext as ProcessWorkAdmissionOwner).processWorkAdmission
    val artworkLoader = rememberCatalogArtworkLoader(runtimeAccess, processWorkAdmission)
    val storyDestinationHost = rememberStoryDestinationHost(
        lifecycleSource = navigationState,
        runtimeAccess = runtimeAccess,
        artworkLoader = artworkLoader,
    )

    AppNavHost(navigationState = navigationState) { route ->
        when (route) {
            is AppRoute.Home -> HomeEntryPoint(
                onExploreManga = { navigationState.select(AppFocusedDestination.MANGA) },
                onExploreLightNovels = {
                    navigationState.select(AppFocusedDestination.LIGHT_NOVEL)
                },
                onStorySelected = { story ->
                    val args = StoryRouteArgs(
                        ref = story.ref,
                        originMediaContext = story.originMediaContext,
                        preview = StoryRoutePreview(
                            title = story.title,
                            coverLocator = story.coverLocator,
                            coverAssetKey = story.coverAssetKey,
                        ),
                    )
                    navigationState.push(
                        AppRoute.Story(StoryRouteCodec.encode(args, newStoryRouteEntryId())),
                    )
                },
                artwork = { story, modifier ->
                    CatalogCoverArtwork(
                        artworkLoader = artworkLoader,
                        title = story.title,
                        locator = story.coverLocator,
                        assetKey = story.coverAssetKey,
                        modifier = modifier,
                    )
                },
            )
            is AppRoute.Discover -> DiscoverDestination(
                route = route,
                runtimeAccess = runtimeAccess,
                artworkLoader = artworkLoader,
                onStoryRoute = navigationState::push,
            )
            is AppRoute.Story -> storyDestinationHost.Content(
                route = route,
                onBack = { navigationState.popFocusedRoute() },
            )
        }
    }
}
