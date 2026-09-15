package app.openstory.catalog.feature.artwork

import app.openstory.artwork.request.ArtworkLocalAsset
import app.openstory.artwork.request.ArtworkLocalResolver
import app.openstory.catalog.feature.R

internal object VariantLocalCoverAssets : ArtworkLocalResolver {
    override fun resolve(logicalAssetId: String, assetVersion: String): ArtworkLocalAsset? {
        if (assetVersion != ASSET_VERSION) return null
        return when (logicalAssetId) {
            "debug:manga:cover-a" -> ArtworkLocalAsset(R.drawable.catalog_debug_manga_a)
            "debug:manga:cover-b" -> ArtworkLocalAsset(R.drawable.catalog_debug_manga_b)
            "debug:light-novel:cover-a" -> ArtworkLocalAsset(R.drawable.catalog_debug_light_novel_a)
            "debug:light-novel:cover-b" -> ArtworkLocalAsset(R.drawable.catalog_debug_light_novel_b)
            else -> null
        }
    }

    private const val ASSET_VERSION = "1"
}
