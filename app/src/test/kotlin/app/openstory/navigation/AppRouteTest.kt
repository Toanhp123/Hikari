package app.openstory.navigation

import app.openstory.common.navigation.RouteEntryId
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
            AppRoute.Story(
                StoryRouteWire(
                    entryId = "story-entry-1",
                    storyId = "story-1",
                    catalogSourceKey = "source-1",
                    sourceStoryId = "src-story-1",
                    originMedia = AppMediaRoute.MANGA,
                    previewTitle = "Preview Title",
                    previewArtwork = ArtworkRoutePreviewWire(
                        authorityKey = "source-1",
                        stableAssetKey = "hikari:v2:cover-asset:v1:story-1:cover:v1:abc",
                        locatorKind = "remote_https",
                        locatorValue = "https://example.com/cover.jpg",
                        locatorAux = null,
                        revision = "cover:v1:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    ),
                ),
            ),
        )

        routes.forEach { route ->
            val encoded = json.encodeToString(AppRoute.serializer(), route)

            assertEquals(route, json.decodeFromString(AppRoute.serializer(), encoded))
        }
    }


    @Test
    fun storyRouteEntryIdsAreSafeAndUniqueIndependentlyOfStoryIdentity() {
        val ids = List(64) { newStoryRouteEntryId() }

        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id -> assertEquals(id, RouteEntryId.from(id.value)) }
    }

    @Test
    fun routeEntryIdRejectsBlankOversizedAndUnsafeWireValues() {
        assertThrows(IllegalArgumentException::class.java) { RouteEntryId.from(" ") }
        assertThrows(IllegalArgumentException::class.java) { RouteEntryId.from("a".repeat(129)) }
        assertThrows(IllegalArgumentException::class.java) { RouteEntryId.from("story/id") }
    }
}
