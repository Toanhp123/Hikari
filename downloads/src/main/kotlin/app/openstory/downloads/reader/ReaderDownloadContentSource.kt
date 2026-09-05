package app.openstory.downloads.reader

import app.openstory.chapters.repository.ChapterReleaseLookup
import app.openstory.downloads.DownloadContentSource
import app.openstory.downloads.DownloadFetchResult
import app.openstory.common.id.ChapterReleaseId
import app.openstory.chapters.model.ChapterRelease
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.isLocalPersistable
import app.openstory.reader.assets.ContentFetchArbiter
import app.openstory.reader.assets.ContentFetchPriority
import app.openstory.reader.routing.ContentSourceExecutionLane
import app.openstory.reader.routing.ContentSourceWorkPriority
import kotlinx.coroutines.CancellationException

class ReaderDownloadContentSource(
    private val chapters: ChapterReleaseLookup,
    private val sources: ReaderDocumentSourceRegistry,
    private val availability: ReaderSourceAvailability,
    private val sourceLane: ContentSourceExecutionLane,
    private val fetchArbiter: ContentFetchArbiter,
) : DownloadContentSource {
    override suspend fun fetch(releaseId: ChapterReleaseId): DownloadFetchResult {
        val release = chapters.findRelease(releaseId)
        return if (release == null) {
            DownloadFetchResult.Failure("download.release_missing", false)
        } else {
            fetch(release)
        }
    }

    private suspend fun fetch(release: ChapterRelease): DownloadFetchResult {
        val isOfflineDownloadSupported =
            release.pluginId in availability.offlineDownloadPluginIds()
        val source = if (isOfflineDownloadSupported) {
            sources.enabled().firstOrNull { it.pluginId == release.pluginId }
        } else {
            null
        }
        return when {
            !isOfflineDownloadSupported ->
                DownloadFetchResult.Failure("download.content_online_only", false)
            source == null -> DownloadFetchResult.Failure("download.source_unavailable", false)
            else -> fetchFromSource(source, release)
        }
    }

    private suspend fun fetchFromSource(
        source: ReaderDocumentSource,
        release: ChapterRelease,
    ): DownloadFetchResult = try {
        val result = sourceLane.withSource(
            source.pluginId,
            ContentSourceWorkPriority.USER_WORK,
        ) {
            fetchArbiter.withAdmission(ContentFetchPriority.USER_WORK) {
                source.fetch(release)
            }
        }
        when (result) {
            is ReaderSourceResult.Success -> result.toDownloadResult()
            is ReaderSourceResult.Failure -> DownloadFetchResult.Failure(result.code, result.retryable)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DownloadFetchResult.Failure("download.source_failed", true)
    }

    private fun ReaderSourceResult.Success.toDownloadResult(): DownloadFetchResult {
        if (!document.isLocalPersistable) {
            return DownloadFetchResult.Failure("download.content_online_only", false)
        }
        val blob = ReaderDocumentBlobCodec.encode(document)
        return DownloadFetchResult.Success(document.fingerprint, blob.bytes(), blob.checksum)
    }
}
