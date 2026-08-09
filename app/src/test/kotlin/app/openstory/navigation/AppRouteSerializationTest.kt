package app.openstory.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AppRouteSerializationTest {
    @Test
    fun topLevelRoutesAreHomeLibraryAndPlugins() {
        assertEquals(
            listOf(
                AppRoute.Home,
                AppRoute.Library,
                AppRoute.Plugins,
            ),
            topLevelRoutes,
        )
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
    fun storyRouteRetainsCatalogSourceIdentity() {
        val route: AppRoute = AppRoute.Story(
            storyId = "story_123",
            pluginId = "catalog.example",
            sourceId = "source_456",
        )
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
}
