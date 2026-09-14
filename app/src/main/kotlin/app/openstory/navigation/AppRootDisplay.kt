package app.openstory.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

@Composable
internal fun AppRootDisplay(
    navigationState: AppNavigationState,
    routeContent: @Composable (AppRoute) -> Unit,
) {
    NavDisplay(
        backStack = navigationState.focusedBackStack,
        onBack = { navigationState.popFocusedRoute() },
        entryProvider = entryProvider<AppRoute> {
            entry<AppRoute.Home> { route -> routeContent(route) }
            entry<AppRoute.Discover> { route -> routeContent(route) }
            entry<AppRoute.Story> { route -> routeContent(route) }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
