package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.identity.StorySourceRef

fun interface CatalogSimilarCapability {
    suspend fun acquireSimilar(
        ref: StorySourceRef,
        maximumItems: Int,
    ): List<CatalogTransientStory>
}
