package app.openstory.story.domain

import app.openstory.model.CatalogSourceMetadata
import app.openstory.model.LanguageTag
import app.openstory.model.PluginId
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.host.HostedPlugin

data class MappedCatalogDetails(
    val pluginId: PluginId,
    val pluginVersion: String,
    val metadata: CatalogSourceMetadata,
)

class CatalogDetailsMapper {
    fun map(
        hosted: HostedPlugin<CatalogPlugin>,
        details: CatalogDetails,
    ): MappedCatalogDetails = MappedCatalogDetails(
        pluginId = hosted.id,
        pluginVersion = hosted.version,
        metadata = CatalogSourceMetadata(
            sourceId = details.sourceId,
            sourceUrl = details.sourceUrl,
            title = details.title,
            aliases = details.aliases.toSet(),
            authors = details.authors.toSet(),
            description = details.description,
            genres = details.genres.toSet(),
            contentType = details.contentType,
            languageTags = details.languageTags.map { tag -> LanguageTag(tag) }.toSet(),
            coverReference = details.image?.url,
            publicationStatus = null,
            score = details.score?.value,
            scoreScale = details.score?.scale,
            popularityRank = details.popularityRank,
        ),
    )
}
