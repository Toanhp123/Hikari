package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.identity.StorySourceRef

fun interface CatalogStoryCapability {
    suspend fun acquireStoryDetail(ref: StorySourceRef): StoryDetailAcquisition
}
