package app.openstory.chapters.source

import app.openstory.plugins.api.protocol.PageDto
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.content.ContentChapterListModeDto
import app.openstory.plugins.api.protocol.content.ContentChaptersRequestDto
import app.openstory.plugins.api.protocol.content.ContentReleaseDto
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.PluginRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class PluginChapterSource(
    installed: InstalledPlugin,
    private val runtime: PluginRuntime,
    private val json: Json,
) : ChapterSource {
    override val pluginId = installed.pluginId
    override val version = installed.version

    private val invocationMutex = Mutex()

    override suspend fun chapters(request: ChapterSourceRequest): ChapterSourceResult = try {
        val input = json.encodeToJsonElement(request.toDto())
        when (val result = invocationMutex.withLock {
            runtime.invoke(pluginId, PluginOperation.CONTENT_CHAPTERS, input)
        }) {
            is PluginCallResult.Success -> decodePage(result)
            is PluginCallResult.Failure -> ChapterSourceResult.Failure(
                ChapterSourceFailure(result.code, result.retryable),
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ChapterSourceResult.Failure(ChapterSourceFailure("chapter.source_failed", true))
    }

    private fun decodePage(
        result: PluginCallResult.Success<JsonElement>,
    ): ChapterSourceResult =
        try {
            val page = json.decodeFromJsonElement(
                PageDto.serializer(ContentReleaseDto.serializer()),
                result.value,
            )
            ChapterSourceResult.Success(
                ChapterSourcePage(page.items.map(ContentReleaseDto::toSource), page.nextToken),
            )
        } catch (_: IllegalArgumentException) {
            ChapterSourceResult.Failure(
                ChapterSourceFailure("chapter.source_payload_invalid", false),
            )
        }
}

private fun ChapterSourceRequest.toDto() = ContentChaptersRequestDto(
    sourceStoryId = sourceStoryId,
    mode = mode.toDto(),
    checkpoint = checkpoint,
    nextToken = nextToken,
)

private fun ChapterListMode.toDto(): ContentChapterListModeDto = when (this) {
    ChapterListMode.RECENT -> ContentChapterListModeDto.RECENT
    ChapterListMode.FULL -> ContentChapterListModeDto.FULL
    ChapterListMode.INCREMENTAL -> ContentChapterListModeDto.INCREMENTAL
}

private fun ContentReleaseDto.toSource() = ChapterSourceRelease(
    sourceReleaseId = sourceReleaseId,
    title = title,
    rawNumber = rawNumber,
    languageTag = languageTag,
    publishedAtEpochMillis = publishedAtEpochMillis,
)
