package app.openstory.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

class AppNavigator(
    internal val backStack: NavBackStack<NavKey>,
) {
    val currentRoute: AppRoute?
        get() = backStack.lastOrNull() as? AppRoute

    fun navigate(route: AppRoute) {
        if (currentRoute != route) backStack.add(route)
    }

    fun selectTopLevel(destination: TopLevelDestination) {
        if (currentRoute == destination.route && backStack.size == 1) return
        backStack.clear()
        backStack.add(destination.route)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator {
    val backStack = rememberNavBackStack(AppRoute.Home)
    return remember(backStack) { AppNavigator(backStack) }
}
