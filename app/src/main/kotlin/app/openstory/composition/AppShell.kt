package app.openstory.composition

import androidx.compose.runtime.Composable
import app.openstory.navigation.AppFocusedDestination
import app.openstory.navigation.AppNavHost
import app.openstory.navigation.AppRoute
import app.openstory.navigation.rememberAppNavigationState

@Composable
internal fun AppShell() {
    val navigationState = rememberAppNavigationState()
    val storyDestinationHost = rememberStoryDestinationHost(navigationState)

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
                onStoryRoute = navigationState::push,
            )
            is AppRoute.Story -> storyDestinationHost.Content(
                route = route,
                onBack = { navigationState.popFocusedRoute() },
            )
        }
    }
}
