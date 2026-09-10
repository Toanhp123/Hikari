package app.openstory.catalog.feature

import androidx.compose.foundation.lazy.LazyListState
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverRevision
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRouteTest {
    @Test
    fun storyRouteSavesOnlyValidatedPrimitiveIdentityAndCoverRevision() {
        val ref = ref("story-17")
        val route = CatalogRoute.Story(
            ref = ref,
            coverAssetKey = CoverAssetKey(ref.storyId, CoverRevision(COVER_REVISION)),
        )

        val saved = CatalogRouteCodec.save(route)

        assertTrue(saved.startsWith("story:"))
        assertTrue(saved.contains(SOURCE_KEY.value))
        assertTrue(saved.contains("story-17"))
        assertTrue(saved.endsWith(COVER_REVISION))
        assertEquals(route, CatalogRouteCodec.restore(saved))
    }

    @Test
    fun malformedOrMismatchedSavedStoryFailsClosedToDiscover() {
        val ref = ref("story-17")

        assertEquals(
            CatalogRoute.Discover,
            CatalogRouteCodec.restore(
                CatalogRouteCodec.save(CatalogRoute.Story(ref, null)).replace("story-17", "story-18"),
            ),
        )
        assertEquals(
            CatalogRoute.Discover,
            CatalogRouteCodec.restore("story:14:not-a-story-id"),
        )
    }

    @Test
    fun storyRouteRejectsCoverIdentityFromAnotherStory() {
        val ref = ref("story-17")
        val otherRef = ref("story-18")

        assertThrows(IllegalArgumentException::class.java) {
            CatalogRoute.Story(
                ref = ref,
                coverAssetKey = CoverAssetKey(otherRef.storyId, CoverRevision(COVER_REVISION)),
            )
        }
    }

    @Test
    fun discoverScrollStateInstanceSurvivesStoryAndBack() {
        val listState = LazyListState(firstVisibleItemIndex = 7, firstVisibleItemScrollOffset = 31)
        val navigation = CatalogNavigationState(listState)

        navigation.showStory(ref("story-17"), null)
        navigation.showDiscover()

        assertEquals(CatalogRoute.Discover, navigation.route)
        assertSame(listState, navigation.discoverListState)
        assertEquals(7, navigation.discoverListState.firstVisibleItemIndex)
        assertEquals(31, navigation.discoverListState.firstVisibleItemScrollOffset)
    }

    private fun ref(sourceStoryId: String): StorySourceRef {
        val sourceKey = SourceStoryKey(SOURCE_KEY, sourceStoryId)
        return StorySourceRef(
            storyId = SourceStoryIdV1.derive(sourceKey),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = sourceStoryId,
        )
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("route-test")
        const val COVER_REVISION =
            "cover:v1:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
