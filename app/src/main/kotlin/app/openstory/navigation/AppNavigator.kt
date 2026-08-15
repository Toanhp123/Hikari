package app.openstory.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal val APP_START_ROUTE: AppRoute = AppRoute.Home

class AppNavigator(
    internal val navigationState: AppNavigationState,
) {
    val currentRoute: AppRoute?
        get() = navigationState.currentRoute

    fun navigate(route: AppRoute) {
        val topLevel = topLevelDestinations.firstOrNull { it.route == route }
        if (topLevel != null) {
            selectTopLevel(topLevel)
        } else if (currentRoute != route) {
            navigationState.activeBackStack.add(route)
        }
    }

    fun selectTopLevel(destination: TopLevelDestination) {
        if (navigationState.topLevelRoute == destination.route) return
        navigationState.topLevelRoute = destination.route
    }

    fun back() {
        val active = navigationState.activeBackStack
        if (active.size > 1) {
            active.removeAt(active.lastIndex)
        } else if (navigationState.topLevelRoute != navigationState.startRoute) {
            navigationState.topLevelRoute = navigationState.startRoute
        }
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator {
    val navigationState = rememberAppNavigationState()
    return remember(navigationState) { AppNavigator(navigationState) }
}
