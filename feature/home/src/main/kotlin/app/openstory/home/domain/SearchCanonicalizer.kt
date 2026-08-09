package app.openstory.home.domain

import app.openstory.catalog.search.CatalogSearchResult
import app.openstory.common.id.PluginId

internal class SearchCanonicalizer {
    fun canonicalize(
        result: CatalogSearchResult,
        versions: Map<PluginId, String>,
    ): List<SearchResultCard> = result.stories.map { story ->
        val primary = story.sources.first()
        SearchResultCard(
            storyId = story.story.id,
            title = primary.title,
            contentType = story.story.contentType,
            sources = story.sources.map { source ->
                SearchResultSource(
                    pluginId = source.pluginId,
                    pluginVersion = versions[source.pluginId].orEmpty(),
                    sourceId = source.sourceId,
                    title = source.title,
                    contentType = source.contentType,
                    authors = source.authors,
                    coverReference = source.coverUrl,
                    score = source.score?.value,
                    scoreScale = source.score?.scale,
                )
            },
        )
    }
}
