package app.openstory.downloads.reader

import app.openstory.chapters.repository.ChapterReleaseLookup
import app.openstory.downloads.DownloadContentSource
import app.openstory.downloads.DownloadFetchResult
import app.openstory.common.id.ChapterReleaseId
import app.openstory.chapters.model.ChapterRelease
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderSourceResult
import kotlinx.coroutines.CancellationException

class ReaderDownloadContentSource(
    private val chapters: ChapterReleaseLookup,
    private val sources: ReaderDocumentSourceRegistry,
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
        val source = sources.enabled().firstOrNull { it.pluginId == release.pluginId }
            ?: return DownloadFetchResult.Failure("download.source_unavailable", false)
        return try {
            when (val result = source.fetch(release)) {
                is ReaderSourceResult.Success -> {
                    val blob = ReaderDocumentBlobCodec.encode(result.document)
                    DownloadFetchResult.Success(result.document.fingerprint, blob.bytes(), blob.checksum)
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
