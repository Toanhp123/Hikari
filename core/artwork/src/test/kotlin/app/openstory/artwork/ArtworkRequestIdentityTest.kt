package app.openstory.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArtworkRequestIdentityTest {
    @Test
    fun varySeparatesEncodedAndDecodedCachesWhileTransformSeparatesOnlyDecodedCache() {
        val base = identity()
        val transformed = identity(transformKey = "fit:120x180")
        val privateScope = identity(varyKey = "auth:user-a")

        assertEquals(base.encodedCacheKey, transformed.encodedCacheKey)
        assertNotEquals(base.decodedCacheKey, transformed.decodedCacheKey)
        assertNotEquals(base.encodedCacheKey, privateScope.encodedCacheKey)
        assertNotEquals(base.decodedCacheKey, privateScope.decodedCacheKey)
        assertEquals(base.encodedCacheKey, identity().encodedCacheKey)
        assertEquals(base.decodedCacheKey, identity().decodedCacheKey)
    }

    private fun identity(
        transformKey: String = "crop:240x360",
        varyKey: String = "public",
    ) = ArtworkRequestIdentity(
        authority = ArtworkAuthorityKey("catalog:test"),
        stableAssetKey = "asset:1",
        locator = "https://images.example/cover",
        transformKey = transformKey,
        varyKey = varyKey,
    )
}
