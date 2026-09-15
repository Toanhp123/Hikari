package app.openstory.catalog.feature.artwork

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.openstory.artwork.request.ArtworkAuthorityKey
import app.openstory.artwork.request.ArtworkLocator
import app.openstory.artwork.request.ArtworkRequest
import app.openstory.artwork.request.ArtworkRequestIdentity
import app.openstory.artwork.runtime.ArtworkLoader
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import coil3.request.ImageRequest

internal fun CoverAssetKey.toImageRequest(
    context: Context,
    locator: CoverLocator?,
): ImageRequest = toArtworkRequest(locator).toImageRequest(context)

internal fun CoverAssetKey.toArtworkRequest(locator: CoverLocator?): ArtworkRequest {
    val artworkLocator = when (locator) {
        null -> null
        is CoverLocator.TrustedLocalResource -> ArtworkLocator.TrustedLocalResource(
            logicalAssetId = locator.logicalAssetId,
            assetVersion = locator.assetVersion,
        )
        is CoverLocator.RemoteHttps -> ArtworkLocator.RemoteHttps(locator.normalizedUri.value)
    }
    val authority = when (locator) {
        is CoverLocator.RemoteHttps -> ArtworkAuthorityKey(locator.catalogSourceKey.value)
        else -> LOCAL_AUTHORITY
    }
    return ArtworkRequest(
        identity = ArtworkRequestIdentity(
            authority = authority,
            stableAssetKey = stableCacheKey,
            locator = artworkLocator?.identity ?: MISSING_LOCATOR,
            transformKey = COVER_TRANSFORM_KEY,
            varyKey = PUBLIC_VARY_KEY,
        ),
        locator = artworkLocator,
    )
}

internal val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader?> { null }

private val LOCAL_AUTHORITY = ArtworkAuthorityKey("hikari:local")
private const val MISSING_LOCATOR = "missing"
private const val COVER_TRANSFORM_KEY = "cover:crop:v1"
private const val PUBLIC_VARY_KEY = "public"
