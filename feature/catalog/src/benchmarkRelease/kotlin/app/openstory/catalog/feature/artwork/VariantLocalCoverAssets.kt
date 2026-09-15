package app.openstory.catalog.feature.artwork

import app.openstory.artwork.request.ArtworkLocalAsset
import app.openstory.artwork.request.ArtworkLocalResolver
import app.openstory.catalog.feature.R

internal object VariantLocalCoverAssets : ArtworkLocalResolver {
    override fun resolve(logicalAssetId: String, assetVersion: String): ArtworkLocalAsset? {
        if (assetVersion != ASSET_VERSION) return null
        return when (logicalAssetId) {
            "benchmark:manga:cover-a" -> ArtworkLocalAsset(R.drawable.catalog_benchmark_manga_a)
            "benchmark:manga:cover-b" -> ArtworkLocalAsset(R.drawable.catalog_benchmark_manga_b)
            "benchmark:light-novel:cover-a" -> ArtworkLocalAsset(R.drawable.catalog_benchmark_light_novel_a)
            "benchmark:light-novel:cover-b" -> ArtworkLocalAsset(R.drawable.catalog_benchmark_light_novel_b)
            else -> null
        }
    }

    private const val ASSET_VERSION = "1"
}
