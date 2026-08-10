package app.openstory.library.matching

import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId

data class ContentStoryFeatures(
    val title: String,
    val aliases: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val contentType: ContentType? = null,
    val directMappings: Set<DirectContentStoryIdentity> = emptySet(),
) {
    init {
        require(title.isNotBlank()) { "Title must not be blank" }
        require(aliases.none(String::isBlank)) { "Aliases must not contain blank values" }
        require(authors.none(String::isBlank)) { "Authors must not contain blank values" }
    }

    val titles: Set<String>
        get() = setOf(title) + aliases
}

data class DirectContentStoryIdentity(
    val pluginId: PluginId,
    val sourceStoryId: String,
) {
    init {
        require(sourceStoryId.isNotBlank()) { "Direct source story identity must not be blank" }
        require(sourceStoryId.none(Char::isISOControl)) {
            "Direct source story identity must not contain control characters"
        }
    }
}
