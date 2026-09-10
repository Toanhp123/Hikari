package app.openstory.catalog.feature.assets

import android.content.Context
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import coil3.request.CachePolicy
import coil3.request.ImageRequest

internal data class CoverRequest(
    val assetKey: CoverAssetKey,
    val locator: CoverLocator?,
) {
    init {
        when (locator) {
            null -> Unit
            is CoverLocator.TrustedLocalResource -> require(
                assetKey.coverRevision == app.openstory.catalog.domain.asset.CoverRevisionV1.local(
                    locator.logicalAssetId,
                    locator.assetVersion,
                ),
            )
            is CoverLocator.RemoteHttps -> require(assetKey.coverRevision == locator.revision)
        }
    }

    fun toImageRequest(context: Context): ImageRequest = ImageRequest.Builder(context)
        .data(this)
        .memoryCacheKey(assetKey.stableCacheKey)
        .diskCacheKey(assetKey.stableCacheKey)
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()
}
