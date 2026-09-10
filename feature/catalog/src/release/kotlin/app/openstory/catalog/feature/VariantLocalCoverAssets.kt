package app.openstory.catalog.feature

import app.openstory.catalog.feature.assets.LocalCoverAsset
import app.openstory.catalog.feature.assets.LocalCoverAssetResolver

internal object VariantLocalCoverAssets : LocalCoverAssetResolver {
    override fun resolve(logicalAssetId: String, assetVersion: String): LocalCoverAsset? = null
}
