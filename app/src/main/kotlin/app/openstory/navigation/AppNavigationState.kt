package app.openstory.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

class AppNavigationState(
    val startRoute: AppRoute,
    private val topLevelRouteState: MutableState<AppRoute>,
    val backStacks: Map<AppRoute, NavBackStack<NavKey>>,
) {
    var topLevelRoute: AppRoute
        get() = topLevelRouteState.value
        set(value) {
            require(value.isTopLevel()) { "Top-level route required: $value" }
            topLevelRouteState.value = value
        }

    val activeBackStack: NavBackStack<NavKey>
        get() = requireNotNull(backStacks[topLevelRoute]) { "Missing stack for $topLevelRoute" }

    val currentRoute: AppRoute?
        get() = activeBackStack.lastOrNull() as? AppRoute

    val stacksInUse: List<AppRoute>
        get() = if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }
}

@Composable
fun rememberAppNavigationState(): AppNavigationState {
    val selectedTopLevel = rememberSaveable(saver = topLevelRouteStateSaver) {
        mutableStateOf(APP_START_ROUTE)
    }
    val discover = rememberNavBackStack(AppRoute.Discover)
    val home = rememberNavBackStack(AppRoute.Home)
    val library = rememberNavBackStack(AppRoute.Library)
    return remember(selectedTopLevel, discover, home, library) {
        AppNavigationState(
            startRoute = APP_START_ROUTE,
            topLevelRouteState = selectedTopLevel,
            backStacks = linkedMapOf(
                AppRoute.Discover to discover,
                AppRoute.Home to home,
                AppRoute.Library to library,
            ),
        )
    }
}

@Composable
fun AppNavigationState.decoratedEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> {
    val discoverEntries = decoratedEntriesFor(AppRoute.Discover, entryProvider)
    val homeEntries = decoratedEntriesFor(AppRoute.Home, entryProvider)
    val libraryEntries = decoratedEntriesFor(AppRoute.Library, entryProvider)
    val entries = mapOf(
        AppRoute.Discover to discoverEntries,
        AppRoute.Home to homeEntries,
        AppRoute.Library to libraryEntries,
    )
    return stacksInUse.flatMap { route -> entries.getValue(route) }
}

@Composable
internal fun AppNavigationState.decoratedEntriesFor(
    route: AppRoute,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> {
    require(route.isTopLevel()) { "Top-level route required: $route" }
    return rememberTopLevelEntries(route, entryProvider)
}

@Composable
private fun AppNavigationState.rememberTopLevelEntries(
    route: AppRoute,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> {
    val storeProvider = rememberViewModelStoreProvider(key = route)
    return rememberDecoratedNavEntries(
        backStack = requireNotNull(backStacks[route]),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(storeProvider),
        ),
        entryProvider = entryProvider,
    )
}

private val topLevelRouteStateSaver = Saver<MutableState<AppRoute>, String>(
    save = { state -> TopLevelDestination.entries.first { it.route == state.value }.name },
    restore = { name -> mutableStateOf(TopLevelDestination.valueOf(name).route) },
)
