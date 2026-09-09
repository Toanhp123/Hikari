package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType

interface CatalogAcquisitionSource {
    suspend fun acquireDiscover(mediaType: CatalogMediaType): DiscoverAcquisition
    suspend fun acquireStoryDetail(ref: StorySourceRef): StoryDetailAcquisition
}
