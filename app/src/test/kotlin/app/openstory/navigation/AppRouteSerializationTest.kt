package app.openstory.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.navigation3.runtime.NavKey
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
    fun storyRouteRoundTrips() {
        val route: AppRoute = AppRoute.Story("story_123")
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
