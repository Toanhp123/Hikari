package app.openstory.catalog.feature

import app.openstory.catalog.feature.assets.LocalCoverAsset
import app.openstory.catalog.feature.assets.LocalCoverAssetResolver

internal object VariantLocalCoverAssets : LocalCoverAssetResolver {
    override fun resolve(logicalAssetId: String, assetVersion: String): LocalCoverAsset? {
        if (assetVersion != ASSET_VERSION) return null
        return when (logicalAssetId) {
            "debug:manga:cover-a" -> LocalCoverAsset(R.drawable.catalog_debug_manga_a)
            "debug:manga:cover-b" -> LocalCoverAsset(R.drawable.catalog_debug_manga_b)
            "debug:light-novel:cover-a" -> LocalCoverAsset(R.drawable.catalog_debug_light_novel_a)
            "debug:light-novel:cover-b" -> LocalCoverAsset(R.drawable.catalog_debug_light_novel_b)
            else -> null
        }
    }

    private const val ASSET_VERSION = "1"
}
