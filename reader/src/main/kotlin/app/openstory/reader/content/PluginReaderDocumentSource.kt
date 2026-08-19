package app.openstory.reader.content

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.content.ChapterDocumentDto
import app.openstory.plugins.api.protocol.content.ContentChapterRequestDto
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.reader.document.DocumentValidationResult
import app.openstory.reader.document.ReaderDocumentSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class PluginReaderDocumentSource(
    installed: InstalledPlugin,
    private val runtime: PluginRuntime,
    private val json: Json,
    private val sanitizer: ReaderDocumentSanitizer,
) : ReaderDocumentSource {
    override val pluginId: PluginId = installed.pluginId
    private val invocationMutex = Mutex()
    private val allowRemoteImages = installed.readerCapability?.remoteImages == true

    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult = try {
        val input = json.encodeToJsonElement(ContentChapterRequestDto(release.sourceReleaseId))
        when (val result = invocationMutex.withLock {
            runtime.invoke(pluginId, PluginOperation.CONTENT_CHAPTER, input)
        }) {
            is PluginCallResult.Failure -> ReaderSourceResult.Failure(result.code, result.retryable)
            is PluginCallResult.Success -> decode(result)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ReaderSourceResult.Failure("reader.source_failed", true)
    }

    private fun decode(result: PluginCallResult.Success<kotlinx.serialization.json.JsonElement>): ReaderSourceResult =
        try {
            when (val sanitized = sanitizer.sanitize(
                json.decodeFromJsonElement<ChapterDocumentDto>(result.value),
                allowRemoteImages = allowRemoteImages,
            )) {
                is DocumentValidationResult.Valid -> ReaderSourceResult.Success(sanitized.document)
                is DocumentValidationResult.Invalid -> ReaderSourceResult.Failure(sanitized.code, false)
            }
        } catch (_: IllegalArgumentException) {
            ReaderSourceResult.Failure("reader.source_payload_invalid", false)
        }
}

class PluginReaderDocumentSourceRegistry(
    private val runtime: PluginRuntime,
    private val json: Json,
    private val sanitizer: ReaderDocumentSanitizer,
) : ReaderDocumentSourceRegistry, ReaderSourceAvailability {
    override suspend fun enabled(): List<ReaderDocumentSource> = enabledPlugins()
        .map { plugin -> PluginReaderDocumentSource(plugin, runtime, json, sanitizer) }

    override suspend fun enabledPluginIds(): Set<PluginId> = enabledPlugins()
        .mapTo(linkedSetOf()) { plugin -> plugin.pluginId }

    override suspend fun offlineDownloadPluginIds(): Set<PluginId> = enabledPlugins()
        .filter { plugin -> plugin.readerCapability?.offlineDownload != false }
        .mapTo(linkedSetOf()) { plugin -> plugin.pluginId }

    private suspend fun enabledPlugins(): List<InstalledPlugin> =
        runtime.enabled(PluginOperation.CONTENT_CHAPTER)
            .sortedBy { plugin -> plugin.pluginId.value }
}
