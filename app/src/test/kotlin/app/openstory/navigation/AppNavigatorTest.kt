package app.openstory.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class AppNavigatorTest {
    @Test
    fun topLevelSwitchRetainsNestedHistoryPerTab() {
        val state = navigationState()
        val navigator = AppNavigator(state)

        navigator.navigate(AppRoute.Search)
        navigator.selectTopLevel(TopLevelDestination.Library)
        navigator.navigate(AppRoute.Story("library-story"))
        navigator.selectTopLevel(TopLevelDestination.Home)

        assertEquals(AppRoute.Search, navigator.currentRoute)
        assertEquals(
            listOf(AppRoute.Home, AppRoute.Search),
            state.backStacks.getValue(AppRoute.Home).toList(),
        )
        assertEquals(
            listOf(AppRoute.Library, AppRoute.Story("library-story")),
            state.backStacks.getValue(AppRoute.Library).toList(),
        )
    }

    @Test
    fun backAtNonStartRootReturnsToRetainedHomeStack() {
        val state = navigationState()
        val navigator = AppNavigator(state)
        navigator.navigate(AppRoute.Search)
        navigator.selectTopLevel(TopLevelDestination.Library)

        navigator.back()

        assertEquals(AppRoute.Search, navigator.currentRoute)
    }

    private fun navigationState() = AppNavigationState(
        startRoute = APP_START_ROUTE,
        topLevelRouteState = mutableStateOf(APP_START_ROUTE),
        backStacks = topLevelDestinations.associate { destination ->
            destination.route to NavBackStack<NavKey>(destination.route)
        },
    )
}
