package app.openstory.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import app.openstory.ui.utilityDestinations

class AppRouteSerializationTest {
    @Test
    fun topLevelRoutesAreDiscoverHomeAndLibrary() {
        assertEquals(
            listOf(
                AppRoute.Discover,
                AppRoute.Home,
                AppRoute.Library,
            ),
            topLevelDestinations.map(TopLevelDestination::route),
        )
        assertEquals(
            listOf("Discover", "Home", "Library"),
            topLevelDestinations.map(TopLevelDestination::label),
        )
        assertEquals(AppRoute.Home, APP_START_ROUTE)
    }

    @Test
    fun routesImplementNavigationKey() {
        fun requireNavKey(route: NavKey): NavKey = route

        assertSame(
            AppRoute.Home,
            requireNavKey(AppRoute.Home),
        )
    }

    @Test
    fun searchRouteRoundTrips() {
        val route: AppRoute = AppRoute.Search
        val encoded = Json.encodeToString(
            AppRoute.serializer(),
            route,
        )

        assertEquals(
            route,
            Json.decodeFromString(
                AppRoute.serializer(),
                encoded,
            ),
        )
    }

    @Test
    fun utilityRoutesRoundTrip() {
        listOf(AppRoute.Downloads, AppRoute.Updates).forEach { route ->
            val encoded = Json.encodeToString(AppRoute.serializer(), route)
            assertEquals(route, Json.decodeFromString(AppRoute.serializer(), encoded))
        }
    }

    @Test
    fun onlyPrimaryProductRoutesAreTopLevel() {
        assertTrue(AppRoute.Discover.isTopLevel())
        assertTrue(AppRoute.Home.isTopLevel())
        assertTrue(AppRoute.Library.isTopLevel())
        assertFalse(AppRoute.Search.isTopLevel())
        assertFalse(AppRoute.Downloads.isTopLevel())
        assertFalse(AppRoute.Story("story").isTopLevel())
        assertFalse(AppRoute.Reader("story", "chapter", null).isTopLevel())
    }

    @Test
    fun floatingNavigationIsLimitedToPrimaryProductRoutes() {
        assertTrue(shouldShowFloatingNavigation(AppRoute.Discover))
        assertTrue(shouldShowFloatingNavigation(AppRoute.Home))
        assertTrue(shouldShowFloatingNavigation(AppRoute.Library))
        assertFalse(shouldShowFloatingNavigation(AppRoute.Search))
        assertFalse(shouldShowFloatingNavigation(AppRoute.Story("story")))
        assertFalse(shouldShowFloatingNavigation(AppRoute.Reader("story", "chapter", null)))
    }

    @Test
    fun utilitySheetOnlyExposesImplementedCheckpointEntries() {
        assertEquals(
            listOf(AppRoute.Downloads, AppRoute.Updates),
            utilityDestinations.map { it.route },
        )
        assertEquals(listOf("Downloads", "Updates"), utilityDestinations.map { it.label })
    }


    @Test
    fun storyRouteRoundTripsWithCanonicalIdentityOnly() {
        val route: AppRoute = AppRoute.Story(storyId = "story_123")
        val encoded = Json.encodeToString(
            AppRoute.serializer(),
            route,
        )

        assertEquals(
            route,
            Json.decodeFromString(
                AppRoute.serializer(),
                encoded,
            ),
        )
        assertTrue("story_123" in encoded)
        assertFalse("pluginId" in encoded)
        assertFalse("sourceId" in encoded)
    }
}
