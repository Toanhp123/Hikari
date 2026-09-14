package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating

data class CatalogTransientStory(
    val ref: StorySourceRef,
    val title: String,
    val contentType: CatalogMediaType,
    val coverAssetKey: CoverAssetKey?,
    val coverLocator: CoverLocator?,
    val supportingText: String?,
    val rating: CatalogRating?,
)
