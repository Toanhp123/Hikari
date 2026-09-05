package app.openstory.reader.content

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.document.ReaderDocument

internal sealed interface ReaderLoadResult {
    data class Success(
        val release: ChapterRelease,
        val document: ReaderDocument,
        val fromStore: Boolean,
        val imageSourcePolicy: ReaderImageSourcePolicy? = null,
        val sourcePluginId: PluginId? = null,
    ) : ReaderLoadResult {
        init {
            require((imageSourcePolicy == null) == (sourcePluginId == null)) {
                "Reader image source policy and producing source must be present together."
            }
            require(!fromStore || imageSourcePolicy == null) {
                "Local Reader documents cannot manufacture remote image source provenance."
            }
        }
    }

    data class Failure(val attempts: List<ReaderLoadFailure>) : ReaderLoadResult
}

data class ReaderLoadFailure(
    val releaseId: ChapterReleaseId,
    val code: String,
    val retryable: Boolean,
)
