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
import kotlinx.coroutines.CancellationException

class ReaderDownloadContentSource(
    private val chapters: ChapterReleaseLookup,
    private val sources: ReaderDocumentSourceRegistry,
    private val availability: ReaderSourceAvailability,
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
        return if (release.pluginId !in availability.offlineDownloadPluginIds()) {
            DownloadFetchResult.Failure("download.content_online_only", false)
        } else {
            val source = sources.enabled().firstOrNull { it.pluginId == release.pluginId }
            if (source == null) {
                DownloadFetchResult.Failure("download.source_unavailable", false)
            } else {
                try {
                    when (val result = source.fetch(release)) {
                        is ReaderSourceResult.Success -> {
                            if (!result.document.isLocalPersistable) {
                                DownloadFetchResult.Failure("download.content_online_only", false)
                            } else {
                                val blob = ReaderDocumentBlobCodec.encode(result.document)
                                DownloadFetchResult.Success(result.document.fingerprint, blob.bytes(), blob.checksum)
                            }
                        }
                        is ReaderSourceResult.Failure -> DownloadFetchResult.Failure(result.code, result.retryable)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    DownloadFetchResult.Failure("download.source_failed", true)
                }
            }
        }
    }
}
