package app.openstory.story.domain

import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceDetails
import app.openstory.model.CatalogSourceMetadata
import app.openstory.model.LanguageTag
import app.openstory.model.PluginId
import app.openstory.model.ContentType

data class MappedCatalogDetails(
    val pluginId: PluginId,
    val pluginVersion: String,
    val metadata: CatalogSourceMetadata,
)

class CatalogDetailsMapper {
    internal fun map(
        source: CatalogSource,
        details: SourceDetails,
    ): MappedCatalogDetails = MappedCatalogDetails(
        pluginId = source.pluginId,
        pluginVersion = source.version,
        metadata = CatalogSourceMetadata(
            sourceId = details.sourceId,
            sourceUrl = details.sourceUrl,
            title = details.title,
            aliases = details.aliases.toSet(),
            authors = details.authors.toSet(),
            description = details.description,
            genres = details.genres.toSet(),
            contentType = details.contentType.toModel(),
            languageTags = details.languageTags.map { tag -> LanguageTag(tag) }.toSet(),
            coverReference = details.coverUrl,
            publicationStatus = null,
            score = details.scoreValue,
            scoreScale = details.scoreScale,
            popularityRank = details.popularityRank,
        ),
    )
}

private fun SourceContentType.toModel(): ContentType = when (this) {
    SourceContentType.LIGHT_NOVEL -> ContentType.LIGHT_NOVEL
    SourceContentType.WEB_NOVEL -> ContentType.WEB_NOVEL
    SourceContentType.MANGA -> ContentType.MANGA
    SourceContentType.ANIME -> ContentType.ANIME
}
