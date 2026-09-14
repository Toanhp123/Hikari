package app.openstory.catalog.domain.read

import app.openstory.catalog.domain.asset.requireAlignedCover
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType

data class StoryRouteArgs(
    val ref: StorySourceRef,
    val originMediaContext: CatalogMediaType,
    val preview: StoryRoutePreview? = null,
) {
    init {
        preview?.let {
            requireAlignedCover(ref, it.coverLocator, it.coverAssetKey)
        }
    }
}
