package app.openstory.catalog.feature

import app.openstory.catalog.feature.assets.LocalCoverAsset
import app.openstory.catalog.feature.assets.LocalCoverAssetResolver

internal object VariantLocalCoverAssets : LocalCoverAssetResolver {
    override fun resolve(logicalAssetId: String, assetVersion: String): LocalCoverAsset? {
        if (assetVersion != ASSET_VERSION) return null
        return when (logicalAssetId) {
            "benchmark:manga:cover-a" -> LocalCoverAsset(R.drawable.catalog_benchmark_manga_a)
            "benchmark:manga:cover-b" -> LocalCoverAsset(R.drawable.catalog_benchmark_manga_b)
            "benchmark:light-novel:cover-a" -> LocalCoverAsset(R.drawable.catalog_benchmark_light_novel_a)
            "benchmark:light-novel:cover-b" -> LocalCoverAsset(R.drawable.catalog_benchmark_light_novel_b)
            else -> null
        }
    }

    private const val ASSET_VERSION = "1"
}
