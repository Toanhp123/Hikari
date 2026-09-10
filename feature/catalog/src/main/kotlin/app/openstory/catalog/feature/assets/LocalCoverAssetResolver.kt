package app.openstory.catalog.feature.assets

import androidx.annotation.DrawableRes

@JvmInline
internal value class LocalCoverAsset(@DrawableRes val resourceId: Int) {
    init {
        require(resourceId != 0)
    }
}

internal fun interface LocalCoverAssetResolver {
    fun resolve(logicalAssetId: String, assetVersion: String): LocalCoverAsset?
}
