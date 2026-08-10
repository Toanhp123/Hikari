package app.openstory.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun storyNavigationCarriesCanonicalIdentityOnly() {
        val backStack = NavBackStack<NavKey>(AppRoute.Home)
        val navigator = AppNavigator(backStack)

        navigator.navigate(AppRoute.Story("story-123"))

        assertEquals(AppRoute.Story("story-123"), navigator.currentRoute)
        assertTrue(backStack.none { route -> route.toString().contains("pluginId") })
        assertTrue(backStack.none { route -> route.toString().contains("sourceId") })
    }

    @Test
    fun selectingTopLevelDestinationReplacesNestedHistory() {
        val backStack = NavBackStack<NavKey>(AppRoute.Home)
        val navigator = AppNavigator(backStack)
        navigator.navigate(AppRoute.Search)
        navigator.navigate(AppRoute.Story("story-123"))

        navigator.selectTopLevel(TopLevelDestination.Library)

        assertEquals(listOf(AppRoute.Library), backStack.toList())
    }
}
