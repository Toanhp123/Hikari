package app.openstory.reader.routing

import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderDocumentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/** Per-execution lazy materialization of REMOTE source effects; LOCAL attempts never touch it. */
internal class ReaderRemoteSourceResolver(
    private val load: suspend () -> Map<PluginId, ReaderDocumentSource>,
) {
    private val mutex = Mutex()
    private var resolved: Map<PluginId, ReaderDocumentSource>? = null

    suspend fun resolve(): Map<PluginId, ReaderDocumentSource> {
        mutex.lock()
        return try {
            resolved ?: loadSafely().also { resolved = it }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun loadSafely(): Map<PluginId, ReaderDocumentSource> = try {
        load()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyMap()
    }
}
