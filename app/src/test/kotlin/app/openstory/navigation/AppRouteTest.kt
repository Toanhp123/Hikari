package app.openstory.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppRouteTest {
    private val json = Json

    @Test
    fun routeWiresRoundTripWithOnlyPrimitiveAppValues() {
        val routes = listOf<AppRoute>(
            AppRoute.Home("home-root"),
            AppRoute.Discover("manga-root", AppMediaRoute.MANGA),
            AppRoute.Discover("light-novel-root", AppMediaRoute.LIGHT_NOVEL),
        )

        routes.forEach { route ->
            val encoded = json.encodeToString(AppRoute.serializer(), route)

            assertEquals(route, json.decodeFromString(AppRoute.serializer(), encoded))
        }
    }

    @Test
    fun routeEntryIdRejectsBlankOversizedAndUnsafeWireValues() {
        assertThrows(IllegalArgumentException::class.java) { RouteEntryId.from(" ") }
        assertThrows(IllegalArgumentException::class.java) { RouteEntryId.from("a".repeat(129)) }
        assertThrows(IllegalArgumentException::class.java) { RouteEntryId.from("story/id") }
    }
}
