package app.openstory.composition.navigation

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.domain.read.StoryRoutePreview
import app.openstory.common.navigation.RouteEntryId
import app.openstory.navigation.AppMediaRoute
import app.openstory.navigation.ArtworkRoutePreviewWire
import app.openstory.navigation.StoryRouteWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRouteCodecTest {
    private val json = Json { ignoreUnknownKeys = false }
    private val sourceKey = CatalogSourceKey("source-alpha")
    private val sourceStoryId = "story-42"
    private val storyId = SourceStoryIdV1.derive(SourceStoryKey(sourceKey, sourceStoryId))
    private val ref = StorySourceRef(storyId, sourceKey, sourceStoryId)
    private val entryId = RouteEntryId.from("entry-test-1")

    @Test
    fun roundTripTrustedLocalCover() {
        val assetId = "banner_default"
        val version = "v1"
        val revision = CoverRevisionV1.local(assetId, version)
        val locator = CoverLocator.TrustedLocalResource(assetId, version)
        val assetKey = CoverAssetKey(storyId, revision)
        val preview = StoryRoutePreview(
            title = "Test Manga Title",
            coverLocator = locator,
            coverAssetKey = assetKey,
        )
        val args = StoryRouteArgs(
            ref = ref,
            originMediaContext = CatalogMediaType.MANGA,
            preview = preview,
        )

        val wire = StoryRouteCodec.encode(args, entryId)
        val encoded = json.encodeToString(wire)
        val wireFromJson = json.decodeFromString<StoryRouteWire>(encoded)
        val decoded = StoryRouteCodec.decode(wireFromJson)

        assertEquals(args, decoded)
        assertEquals(AppMediaRoute.MANGA, wire.originMedia)
        assertEquals(StoryRouteCodec.LOCATOR_KIND_LOCAL, wire.previewArtwork?.locatorKind)
    }

    @Test
    fun roundTripRemoteHttpsCover() {
        val uri = RemoteHttpsUriV1.parseAndNormalize("https://cdn.example.com/art/42.jpg")
        val revision = CoverRevisionV1.remoteUri(uri)
        val locator = CoverLocator.RemoteHttps(sourceKey, uri, revision)
        val assetKey = CoverAssetKey(storyId, revision)
        val preview = StoryRoutePreview(
            title = "Light Novel Title",
            coverLocator = locator,
            coverAssetKey = assetKey,
        )
        val args = StoryRouteArgs(
            ref = ref,
            originMediaContext = CatalogMediaType.LIGHT_NOVEL,
            preview = preview,
        )

        val wire = StoryRouteCodec.encode(args, entryId)
        val encoded = json.encodeToString(wire)
        val wireFromJson = json.decodeFromString<StoryRouteWire>(encoded)
        val decoded = StoryRouteCodec.decode(wireFromJson)

        assertEquals(args, decoded)
        assertEquals(AppMediaRoute.LIGHT_NOVEL, wire.originMedia)
        assertEquals(StoryRouteCodec.LOCATOR_KIND_REMOTE_HTTPS, wire.previewArtwork?.locatorKind)
    }

    @Test
    fun roundTripMinimalNoPreview() {
        val args = StoryRouteArgs(
            ref = ref,
            originMediaContext = CatalogMediaType.MANGA,
            preview = null,
        )

        val wire = StoryRouteCodec.encode(args, entryId)
        val encoded = json.encodeToString(wire)
        val decoded = StoryRouteCodec.decode(json.decodeFromString<StoryRouteWire>(encoded))

        assertEquals(args, decoded)
        assertNull(wire.previewTitle)
        assertNull(wire.previewArtwork)
    }

    @Test
    fun roundTripTitleOnlyPreview() {
        val args = StoryRouteArgs(
            ref = ref,
            originMediaContext = CatalogMediaType.LIGHT_NOVEL,
            preview = StoryRoutePreview(title = "Title Only"),
        )

        val wire = StoryRouteCodec.encode(args, entryId)
        val encoded = json.encodeToString(wire)
        val decoded = StoryRouteCodec.decode(json.decodeFromString<StoryRouteWire>(encoded))

        assertEquals(args, decoded)
        assertEquals("Title Only", wire.previewTitle)
        assertNull(wire.previewArtwork)
    }

    @Test
    fun malformedStoryIdMismatchFailsClosed() {
        val wire = StoryRouteWire(
            entryId = "entry-1",
            storyId = "tampered-story-id",
            catalogSourceKey = sourceKey.value,
            sourceStoryId = sourceStoryId,
            originMedia = AppMediaRoute.MANGA,
            previewTitle = "Title",
            previewArtwork = null,
        )

        assertThrows(IllegalArgumentException::class.java) {
            StoryRouteCodec.decode(wire)
        }
        assertNull(StoryRouteCodec.decodeOrNull(wire))
    }

    @Test
    fun malformedCoverRevisionMismatchFailsClosed() {
        val bogusRevision = "cover:v1:" + "f".repeat(64)
        val wire = StoryRouteWire(
            entryId = "entry-1",
            storyId = storyId.value,
            catalogSourceKey = sourceKey.value,
            sourceStoryId = sourceStoryId,
            originMedia = AppMediaRoute.MANGA,
            previewTitle = "Title",
            previewArtwork = ArtworkRoutePreviewWire(
                authorityKey = "local",
                stableAssetKey = "hikari:v2:cover-asset:v1:${storyId.value}:$bogusRevision",
                locatorKind = StoryRouteCodec.LOCATOR_KIND_LOCAL,
                locatorValue = "asset_id",
                locatorAux = "v1",
                revision = bogusRevision,
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            StoryRouteCodec.decode(wire)
        }
        assertNull(StoryRouteCodec.decodeOrNull(wire))
    }

    @Test
    fun malformedRemoteAuthorityMismatchFailsClosed() {
        val uri = RemoteHttpsUriV1.parseAndNormalize("https://cdn.example.com/art.jpg")
        val revision = CoverRevisionV1.remoteUri(uri)
        val wire = StoryRouteWire(
            entryId = "entry-1",
            storyId = storyId.value,
            catalogSourceKey = sourceKey.value,
            sourceStoryId = sourceStoryId,
            originMedia = AppMediaRoute.MANGA,
            previewTitle = "Title",
            previewArtwork = ArtworkRoutePreviewWire(
                authorityKey = "wrong-authority",
                stableAssetKey = "hikari:v2:cover-asset:v1:${storyId.value}:${revision.value}",
                locatorKind = StoryRouteCodec.LOCATOR_KIND_REMOTE_HTTPS,
                locatorValue = uri.value,
                locatorAux = null,
                revision = revision.value,
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            StoryRouteCodec.decode(wire)
        }
        assertNull(StoryRouteCodec.decodeOrNull(wire))
    }


    @Test
    fun malformedLocalAuthorityFailsClosed() {
        val assetId = "asset_id"
        val version = "v1"
        val revision = CoverRevisionV1.local(assetId, version)
        val wire = StoryRouteWire(
            entryId = "entry-1",
            storyId = storyId.value,
            catalogSourceKey = sourceKey.value,
            sourceStoryId = sourceStoryId,
            originMedia = AppMediaRoute.MANGA,
            previewTitle = "Title",
            previewArtwork = ArtworkRoutePreviewWire(
                authorityKey = sourceKey.value,
                stableAssetKey = CoverAssetKey(storyId, revision).stableCacheKey,
                locatorKind = StoryRouteCodec.LOCATOR_KIND_LOCAL,
                locatorValue = assetId,
                locatorAux = version,
                revision = revision.value,
            ),
        )

        assertNull(StoryRouteCodec.decodeOrNull(wire))
    }

    @Test
    fun malformedRemoteAuxiliaryValueFailsClosed() {
        val uri = RemoteHttpsUriV1.parseAndNormalize("https://cdn.example.com/art.jpg")
        val revision = CoverRevisionV1.remoteUri(uri)
        val wire = StoryRouteWire(
            entryId = "entry-1",
            storyId = storyId.value,
            catalogSourceKey = sourceKey.value,
            sourceStoryId = sourceStoryId,
            originMedia = AppMediaRoute.MANGA,
            previewTitle = "Title",
            previewArtwork = ArtworkRoutePreviewWire(
                authorityKey = sourceKey.value,
                stableAssetKey = CoverAssetKey(storyId, revision).stableCacheKey,
                locatorKind = StoryRouteCodec.LOCATOR_KIND_REMOTE_HTTPS,
                locatorValue = uri.value,
                locatorAux = "unexpected",
                revision = revision.value,
            ),
        )

        assertNull(StoryRouteCodec.decodeOrNull(wire))
    }

    @Test
    fun malformedEntryIdFailsClosed() {
        val wire = StoryRouteWire(
            entryId = "invalid/entry id",
            storyId = storyId.value,
            catalogSourceKey = sourceKey.value,
            sourceStoryId = sourceStoryId,
            originMedia = AppMediaRoute.MANGA,
            previewTitle = "Title",
            previewArtwork = null,
        )

        assertThrows(IllegalArgumentException::class.java) {
            StoryRouteCodec.decode(wire)
        }
        assertNull(StoryRouteCodec.decodeOrNull(wire))
    }

    @Test
    fun serializedWireContainsOnlyPrimitiveFields() {
        val wire = StoryRouteWire(
            entryId = "entry-1",
            storyId = storyId.value,
            catalogSourceKey = sourceKey.value,
            sourceStoryId = sourceStoryId,
            originMedia = AppMediaRoute.MANGA,
            previewTitle = "Title",
            previewArtwork = null,
        )

        val encoded = json.encodeToString(wire)
        assertTrue(encoded.contains("\"entryId\":\"entry-1\""))
        assertTrue(encoded.contains("\"originMedia\":\"MANGA\""))
        assertTrue(!encoded.contains("bitmap") && !encoded.contains("runtime") && !encoded.contains("domain"))
    }
}
