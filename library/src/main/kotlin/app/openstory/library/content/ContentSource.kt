package app.openstory.library.content

import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId

data class ContentSourceStory(
    val sourceStoryId: String,
    val title: String,
    val aliases: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val contentType: ContentType? = null,
    val sourceUrl: String? = null,
) {
    init {
        require(sourceStoryId.isNotBlank()) { "Source story ID must not be blank" }
        require(title.isNotBlank()) { "Source story title must not be blank" }
    }
}

interface ContentSource {
    val pluginId: PluginId
    val version: String
    val allowedHosts: Set<String>

    suspend fun search(query: String, limit: Int): ContentSourceResult<List<ContentSourceStory>>

    suspend fun resolveUrl(url: String): ContentSourceResult<ContentSourceStory>
}

interface ContentSourceRegistry {
    suspend fun enabled(): List<ContentSource>
}

sealed interface ContentSourceResult<out T> {
    data class Success<T>(val value: T) : ContentSourceResult<T>

    data class Failure(val failure: ContentSourceFailure) : ContentSourceResult<Nothing>
}

data class ContentSourceFailure(
    val code: String,
    val retryable: Boolean,
)
