package app.openstory.catalog.domain.read

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.limits.requireScalarBound

data class StoryRoutePreview(
    val title: String? = null,
    val coverLocator: CoverLocator? = null,
    val coverAssetKey: CoverAssetKey? = null,
) {
    init {
        title?.let {
            requireScalarBound(
                value = it,
                maximumScalars = CatalogInputLimits.TITLE_UNICODE_SCALARS,
                requireNonBlank = true,
            )
        }
    }
}
