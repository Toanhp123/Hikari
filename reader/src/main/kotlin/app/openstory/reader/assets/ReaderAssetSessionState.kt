package app.openstory.reader.assets

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.routing.ReaderSessionId

data class ReaderAssetSessionState(
    val manifestRevision: Long = 0L,
    val chapterWindowRevision: Long = 0L,
    val committedChapterId: CanonicalChapterId? = null,
    val recentCommittedChapterIds: List<CanonicalChapterId> = emptyList(),
) {
    init {
        require(manifestRevision >= 0L) { "Reader asset manifest revision must not be negative." }
        require(chapterWindowRevision >= 0L) { "Reader asset chapter-window revision must not be negative." }
        require(recentCommittedChapterIds.size <= RECENT_HISTORY_DEPTH) {
            "Reader asset recent history exceeds the bounded depth."
        }
    }

    fun acceptCommitted(
        effectiveManifestRevision: Long,
        chapterId: CanonicalChapterId,
    ): ReaderAssetSessionState {
        require(effectiveManifestRevision > manifestRevision) {
            "Reader asset manifest revision must advance monotonically."
        }
        if (chapterId == committedChapterId) {
            return copy(manifestRevision = effectiveManifestRevision)
        }
        val recent = committedChapterId
            ?.let { previous -> listOf(previous) + recentCommittedChapterIds.filterNot { it == previous } }
            ?.take(RECENT_HISTORY_DEPTH)
            .orEmpty()
        return copy(
            manifestRevision = effectiveManifestRevision,
            chapterWindowRevision = chapterWindowRevision + 1L,
            committedChapterId = chapterId,
            recentCommittedChapterIds = recent,
        )
    }

    private companion object {
        const val RECENT_HISTORY_DEPTH = 2
    }
}

data class ReaderPrefetchedDocumentArtifact(
    val sessionId: ReaderSessionId,
    val prefetchToken: Long,
    val graphRevision: ReaderChapterGraphRevision,
    val targetChapterId: CanonicalChapterId,
    val selectedRelease: ChapterRelease,
    val document: ReaderDocument,
    val imageSourcePolicy: ReaderImageSourcePolicy?,
    val sourcePluginId: PluginId?,
) {
    init {
        require(prefetchToken > 0L) { "Reader prefetch token must be positive." }
        require(selectedRelease.canonicalChapterId == targetChapterId) {
            "Reader prefetched release must belong to the target chapter."
        }
        require((imageSourcePolicy == null) == (sourcePluginId == null)) {
            "Reader prefetch source policy and producing source must be present together."
        }
        require(sourcePluginId == null || sourcePluginId == selectedRelease.pluginId) {
            "Reader prefetched producing source must match the selected release."
        }
    }
}
