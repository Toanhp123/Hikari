package app.openstory.library.content

import app.openstory.catalog.model.ContentType
import app.openstory.plugins.api.protocol.PageDto
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.catalog.WireContentType
import app.openstory.plugins.api.protocol.content.ContentResolveUrlRequestDto
import app.openstory.plugins.api.protocol.content.ContentSearchRequestDto
import app.openstory.plugins.api.protocol.content.ContentStoryCandidateDto
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.PluginRuntime
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class PluginContentSource(
    private val installed: InstalledPlugin,
    private val runtime: PluginRuntime,
    private val json: Json,
) : ContentSource {
    override val pluginId = installed.pluginId
    override val version = installed.version
    override val allowedHosts = installed.allowedNetworkHosts

    private val invocationMutex = Mutex()

    override suspend fun search(
        query: String,
        limit: Int,
    ): ContentSourceResult<List<ContentSourceStory>> {
        require(limit in 1..MAX_LIBRARY_CANDIDATES) { "Content search limit must be bounded" }
        return invoke(
            operation = PluginOperation.CONTENT_SEARCH,
            input = json.encodeToJsonElement(ContentSearchRequestDto(query)),
            serializer = PageDto.serializer(ContentStoryCandidateDto.serializer()),
        ) { page -> page.items.take(limit).map(ContentStoryCandidateDto::toSource) }
    }

    override suspend fun resolveUrl(url: String): ContentSourceResult<ContentSourceStory> {
        if (!isAllowedUrl(url)) {
            return ContentSourceResult.Failure(ContentSourceFailure("content.url_host_denied", false))
        }
        return invoke(
            operation = PluginOperation.CONTENT_RESOLVE_URL,
            input = json.encodeToJsonElement(ContentResolveUrlRequestDto(url)),
            serializer = ContentStoryCandidateDto.serializer(),
            transform = ContentStoryCandidateDto::toSource,
        ).mapUnsupportedUrlOperation()
    }

    private suspend fun <Wire, Source> invoke(
        operation: PluginOperation,
        input: JsonElement,
        serializer: KSerializer<Wire>,
        transform: (Wire) -> Source,
    ): ContentSourceResult<Source> = try {
        when (val result = invocationMutex.withLock { runtime.invoke(pluginId, operation, input) }) {
            is PluginCallResult.Success -> try {
                ContentSourceResult.Success(
                    transform(json.decodeFromJsonElement(serializer, result.value)),
                )
            } catch (_: IllegalArgumentException) {
                ContentSourceResult.Failure(ContentSourceFailure("content.source_payload_invalid", false))
            }
            is PluginCallResult.Failure -> ContentSourceResult.Failure(
                ContentSourceFailure(result.code, result.retryable),
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ContentSourceResult.Failure(ContentSourceFailure("content.source_failed", true))
    }

    private fun isAllowedUrl(value: String): Boolean = value.length <= MAX_URL_LENGTH && runCatching { URI(value) }
        .getOrNull()
        ?.takeIf { uri -> uri.scheme == "https" && uri.userInfo == null }
        ?.host
        ?.lowercase(Locale.ROOT)
        ?.let(allowedHosts::contains) == true

    private companion object {
        const val MAX_LIBRARY_CANDIDATES = 200
        const val MAX_URL_LENGTH = 4_096
    }
}

private fun ContentStoryCandidateDto.toSource() = ContentSourceStory(
    sourceStoryId = sourceStoryId,
    title = title,
    aliases = aliases.toSet(),
    authors = authors.toSet(),
    contentType = contentType?.toModel(),
    sourceUrl = sourceUrl,
)

private fun WireContentType.toModel(): ContentType = when (this) {
    WireContentType.LIGHT_NOVEL -> ContentType.LIGHT_NOVEL
    WireContentType.WEB_NOVEL -> ContentType.WEB_NOVEL
    WireContentType.MANGA -> ContentType.MANGA
    WireContentType.ANIME -> ContentType.ANIME
}

private fun <T> ContentSourceResult<T>.mapUnsupportedUrlOperation(): ContentSourceResult<T> = when (this) {
    is ContentSourceResult.Success -> this
    is ContentSourceResult.Failure -> if (failure.code == "plugin.operation_unavailable") {
        ContentSourceResult.Failure(ContentSourceFailure("content.url_resolution_unsupported", false))
    } else {
        this
    }
}
