package app.openstory.reader.content

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.PluginId
import app.openstory.reader.document.ReaderDocument

interface ReaderDocumentSource {
    val pluginId: PluginId
    suspend fun fetch(release: ChapterRelease): ReaderSourceResult
}

interface ReaderDocumentSourceRegistry {
    suspend fun enabled(): List<ReaderDocumentSource>
}

fun interface ReaderSourceAvailability {
    suspend fun enabledPluginIds(): Set<PluginId>
}

sealed interface ReaderSourceResult {
    data class Success(val document: ReaderDocument) : ReaderSourceResult
    data class Failure(val code: String, val retryable: Boolean) : ReaderSourceResult
}
