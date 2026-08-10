package app.openstory.catalog.model

import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class CatalogEntry(
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceId: String,
    val title: String,
    val aliases: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val description: String? = null,
    val genres: Set<String> = emptySet(),
    val contentType: ContentType,
    val languageTags: Set<String> = emptySet(),
    val coverUrl: String? = null,
    val sourceUrl: String? = null,
    val score: Score? = null,
    val popularityRank: Long? = null,
) {
    init {
        require(sourceId.isNotBlank()) { "Source identity must not be blank" }
        require(title.isNotBlank()) { "Title must not be blank" }
    }
}
