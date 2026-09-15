package app.openstory.catalog.feature.artwork

import app.openstory.artwork.request.ArtworkLocalAsset
import app.openstory.artwork.request.ArtworkLocalResolver

internal object VariantLocalCoverAssets : ArtworkLocalResolver {
    override fun resolve(logicalAssetId: String, assetVersion: String): ArtworkLocalAsset? = null
}
