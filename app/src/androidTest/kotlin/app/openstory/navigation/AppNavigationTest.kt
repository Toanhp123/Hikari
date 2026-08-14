package app.openstory.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.ui.HikariAppShell
import app.openstory.ui.HikariUtilitySheet
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

class AppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

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

    @Test
    fun utilityDestinationPreservesOriginForBackNavigation() {
        val backStack = NavBackStack<NavKey>(AppRoute.Library)
        val navigator = AppNavigator(backStack)
        var sheetOpen = true
        composeRule.setContent {
            HikariTheme {
                HikariUtilitySheet(
                    onDismiss = { sheetOpen = false },
                    onDestinationSelected = { route ->
                        sheetOpen = false
                        navigator.navigate(route)
                    },
                )
            }
        }

        composeRule.onNodeWithText("Downloads").performClick()
        composeRule.runOnIdle {
            assertEquals(false, sheetOpen)
            assertEquals(AppRoute.Downloads, navigator.currentRoute)
            navigator.back()
            assertEquals(AppRoute.Library, navigator.currentRoute)
        }
    }

    @Test
    fun storyRouteNeverExposesFloatingNavigation() {
        assertFocusedRouteHasNoFloatingNavigation(AppRoute.Story("story-123"))
    }

    @Test
    fun readerRouteNeverExposesFloatingNavigation() {
        assertFocusedRouteHasNoFloatingNavigation(
            AppRoute.Reader("story-123", "chapter-1", null),
        )
    }

    private fun assertFocusedRouteHasNoFloatingNavigation(route: AppRoute) {
        composeRule.setContent {
            HikariTheme {
                HikariAppShell(route, {}, {}) { _ -> }
            }
        }
        composeRule.onAllNodesWithText("Discover").assertCountEquals(0)
        composeRule.onAllNodesWithText("Home").assertCountEquals(0)
        composeRule.onAllNodesWithText("Library").assertCountEquals(0)
    }
}
