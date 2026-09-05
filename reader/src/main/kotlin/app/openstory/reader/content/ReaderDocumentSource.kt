package app.openstory.reader.content

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.ReaderImageIdentityContract
import app.openstory.plugins.api.manifest.ReaderImageLocatorContract
import app.openstory.plugins.api.manifest.ReaderImagePersistenceContract
import app.openstory.reader.document.ReaderDocument

data class ReaderImageSourcePolicy(
    val identityContract: ReaderImageIdentityContract,
    val locatorContract: ReaderImageLocatorContract,
    val persistenceContract: ReaderImagePersistenceContract,
) {
    companion object {
        val FAIL_CLOSED = ReaderImageSourcePolicy(
            ReaderImageIdentityContract.DELIVERY_STABLE_ONLY,
            ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN,
            ReaderImagePersistenceContract.NON_PERSISTENT,
        )
    }
}

interface ReaderDocumentSource {
    val pluginId: PluginId
    val imageSourcePolicy: ReaderImageSourcePolicy
        get() = ReaderImageSourcePolicy.FAIL_CLOSED
    suspend fun fetch(release: ChapterRelease): ReaderSourceResult
}

interface ReaderDocumentSourceRegistry {
    suspend fun enabled(): List<ReaderDocumentSource>
}

fun interface ReaderSourceAvailability {
    suspend fun enabledPluginIds(): Set<PluginId>

    suspend fun offlineDownloadPluginIds(): Set<PluginId> = enabledPluginIds()
}

sealed interface ReaderSourceResult {
    data class Success(val document: ReaderDocument) : ReaderSourceResult
    data class Failure(val code: String, val retryable: Boolean) : ReaderSourceResult
}
