package app.openstory.composition.navigation

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevision
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.asset.requireAlignedCover
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.domain.read.StoryRoutePreview
import app.openstory.common.id.StoryId
import app.openstory.common.navigation.RouteEntryId
import app.openstory.navigation.AppMediaRoute
import app.openstory.navigation.ArtworkRoutePreviewWire
import app.openstory.navigation.StoryRouteWire

internal object StoryRouteCodec {
    const val LOCATOR_KIND_LOCAL = "trusted_local"
    const val LOCATOR_KIND_REMOTE_HTTPS = "remote_https"
    private const val LOCAL_AUTHORITY_KEY = "local"

    fun encode(args: StoryRouteArgs, entryId: RouteEntryId): StoryRouteWire {
        val originMedia = when (args.originMediaContext) {
            CatalogMediaType.MANGA -> AppMediaRoute.MANGA
            CatalogMediaType.LIGHT_NOVEL -> AppMediaRoute.LIGHT_NOVEL
        }
        val preview = args.preview
        val locator = preview?.coverLocator
        val assetKey = preview?.coverAssetKey
        val previewArtwork = if (locator != null && assetKey != null) {
            locator.toWire(assetKey)
        } else {
            null
        }

        return StoryRouteWire(
            entryId = entryId.value,
            storyId = args.ref.storyId.value,
            catalogSourceKey = args.ref.catalogSourceKey.value,
            sourceStoryId = args.ref.sourceStoryId,
            originMedia = originMedia,
            previewTitle = preview?.title,
            previewArtwork = previewArtwork,
        )
    }

    fun decode(wire: StoryRouteWire): StoryRouteArgs {
        RouteEntryId.from(wire.entryId)
        val storyId = StoryId(wire.storyId)
        val catalogSourceKey = CatalogSourceKey(wire.catalogSourceKey)
        val ref = StorySourceRef(
            storyId = storyId,
            catalogSourceKey = catalogSourceKey,
            sourceStoryId = wire.sourceStoryId,
        )
        val originMediaContext = when (wire.originMedia) {
            AppMediaRoute.MANGA -> CatalogMediaType.MANGA
            AppMediaRoute.LIGHT_NOVEL -> CatalogMediaType.LIGHT_NOVEL
        }

        return StoryRouteArgs(
            ref = ref,
            originMediaContext = originMediaContext,
            preview = decodePreview(wire, ref),
        )
    }

    fun decodeOrNull(wire: StoryRouteWire): StoryRouteArgs? = try {
        decode(wire)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun CoverLocator.toWire(assetKey: CoverAssetKey): ArtworkRoutePreviewWire = when (this) {
        is CoverLocator.TrustedLocalResource -> ArtworkRoutePreviewWire(
            authorityKey = LOCAL_AUTHORITY_KEY,
            stableAssetKey = assetKey.stableCacheKey,
            locatorKind = LOCATOR_KIND_LOCAL,
            locatorValue = logicalAssetId,
            locatorAux = assetVersion,
            revision = assetKey.coverRevision.value,
        )
        is CoverLocator.RemoteHttps -> ArtworkRoutePreviewWire(
            authorityKey = catalogSourceKey.value,
            stableAssetKey = assetKey.stableCacheKey,
            locatorKind = LOCATOR_KIND_REMOTE_HTTPS,
            locatorValue = normalizedUri.value,
            locatorAux = null,
            revision = revision.value,
        )
    }

    private fun decodePreview(
        wire: StoryRouteWire,
        ref: StorySourceRef,
    ): StoryRoutePreview? {
        if (wire.previewTitle == null && wire.previewArtwork == null) return null
        val (coverLocator, coverAssetKey) = wire.previewArtwork
            ?.let { decodeArtwork(it, ref) }
            ?: (null to null)
        return StoryRoutePreview(
            title = wire.previewTitle,
            coverLocator = coverLocator,
            coverAssetKey = coverAssetKey,
        )
    }

    private fun decodeArtwork(
        artwork: ArtworkRoutePreviewWire,
        ref: StorySourceRef,
    ): Pair<CoverLocator, CoverAssetKey> {
        val revision = CoverRevision(artwork.revision)
        val assetKey = CoverAssetKey(storyId = ref.storyId, coverRevision = revision)
        require(assetKey.stableCacheKey == artwork.stableAssetKey) {
            "Cover stable cache key mismatch"
        }

        val locator = when (artwork.locatorKind) {
            LOCATOR_KIND_LOCAL -> decodeLocalArtwork(artwork, revision)
            LOCATOR_KIND_REMOTE_HTTPS -> decodeRemoteArtwork(artwork, ref.catalogSourceKey, revision)
            else -> throw IllegalArgumentException("Unknown locator kind: ${artwork.locatorKind}")
        }
        requireAlignedCover(ref, locator, assetKey)
        return locator to assetKey
    }

    private fun decodeLocalArtwork(
        artwork: ArtworkRoutePreviewWire,
        revision: CoverRevision,
    ): CoverLocator.TrustedLocalResource {
        require(artwork.authorityKey == LOCAL_AUTHORITY_KEY) {
            "Local cover authorityKey must be $LOCAL_AUTHORITY_KEY"
        }
        val assetVersion = requireNotNull(artwork.locatorAux) {
            "Local cover assetVersion (locatorAux) must not be null"
        }
        require(revision == CoverRevisionV1.local(artwork.locatorValue, assetVersion)) {
            "Local cover revision mismatch"
        }
        return CoverLocator.TrustedLocalResource(
            logicalAssetId = artwork.locatorValue,
            assetVersion = assetVersion,
        )
    }

    private fun decodeRemoteArtwork(
        artwork: ArtworkRoutePreviewWire,
        catalogSourceKey: CatalogSourceKey,
        revision: CoverRevision,
    ): CoverLocator.RemoteHttps {
        require(artwork.locatorAux == null) { "Remote cover locatorAux must be null" }
        require(artwork.authorityKey == catalogSourceKey.value) {
            "Remote cover authorityKey does not match story catalogSourceKey"
        }
        return CoverLocator.RemoteHttps(
            catalogSourceKey = catalogSourceKey,
            normalizedUri = RemoteHttpsUriV1.parseAndNormalize(artwork.locatorValue),
            revision = revision,
        )
    }
}
