package app.openstory.composition

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.openstory.HikariApplication
import app.openstory.catalog.feature.rememberCatalogArtworkLoader
import app.openstory.catalog.feature.rememberCatalogRuntimeAccess
import app.openstory.navigation.AppFocusedDestination
import app.openstory.navigation.AppNavHost
import app.openstory.navigation.AppRoute
import app.openstory.navigation.rememberAppNavigationState

@Composable
internal fun AppShell() {
    val navigationState = rememberAppNavigationState()
    val runtimeAccess = rememberCatalogRuntimeAccess()
    val application = LocalContext.current.applicationContext as HikariApplication
    val artworkLoader = rememberCatalogArtworkLoader(runtimeAccess, application.processWorkAdmission)
    val storyDestinationHost = rememberStoryDestinationHost(
        lifecycleSource = navigationState,
        runtimeAccess = runtimeAccess,
        artworkLoader = artworkLoader,
    )

    AppNavHost(navigationState = navigationState) { route ->
        when (route) {
            is AppRoute.Home -> HomeDestination(
                onExploreManga = { navigationState.select(AppFocusedDestination.MANGA) },
                onExploreLightNovels = {
                    navigationState.select(AppFocusedDestination.LIGHT_NOVEL)
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
