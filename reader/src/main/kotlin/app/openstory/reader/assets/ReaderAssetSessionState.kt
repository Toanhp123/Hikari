package app.openstory.reader.assets

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.routing.ReaderSessionId

data class ReaderAssetSessionState(
    val manifestRevision: Long = 0L,
    val chapterWindowRevision: Long = 0L,
    val committedChapterId: CanonicalChapterId? = null,
    val recentCommittedChapterIds: List<CanonicalChapterId> = emptyList(),
    val committedManifest: ReaderAssetChapterManifest? = null,
    val recentCommittedManifests: List<ReaderAssetChapterManifest> = emptyList(),
    val viewportRevision: Long = 0L,
    val viewport: ReaderViewportSnapshot? = null,
    val activeProtections: ReaderAssetActiveProtections = ReaderAssetActiveProtections.EMPTY,
    val localPresence: Map<ReaderPageAssetKey, ReaderAssetLocalPresence> = emptyMap(),
    val consumedKeys: Set<ReaderPageAssetKey> = emptySet(),
    val prefetchedManifest: ReaderAssetChapterManifest? = null,
    val prefetchToken: Long = 0L,
    val plan: ReaderAssetPlan = ReaderAssetPlan.EMPTY,
) {
    init {
        require(manifestRevision >= 0L) { "Reader asset manifest revision must not be negative." }
        require(chapterWindowRevision >= 0L) { "Reader asset chapter-window revision must not be negative." }
        require(recentCommittedChapterIds.size <= RECENT_HISTORY_DEPTH) {
            "Reader asset recent history exceeds the bounded depth."
        }
        require(recentCommittedManifests.size <= RECENT_HISTORY_DEPTH) {
            "Reader asset recent manifest history exceeds the bounded depth."
        }
        require(viewportRevision >= 0L) { "Reader asset viewport revision must not be negative." }
        require(prefetchToken >= 0L) { "Reader asset prefetch token must not be negative." }
    }

    fun acceptCommitted(
        effectiveManifestRevision: Long,
        chapterId: CanonicalChapterId,
        manifest: ReaderAssetChapterManifest? = null,
    ): ReaderAssetSessionState {
        require(effectiveManifestRevision > manifestRevision) {
            "Reader asset manifest revision must advance monotonically."
        }
        if (chapterId == committedChapterId) {
            val nextKeys = manifest?.descriptors?.mapTo(linkedSetOf()) { it.key }.orEmpty()
            return copy(
                manifestRevision = effectiveManifestRevision,
                committedManifest = manifest ?: committedManifest,
                viewport = null,
                activeProtections = ReaderAssetActiveProtections.EMPTY,
                localPresence = manifest?.descriptors
                    ?.associate { it.key to ReaderAssetLocalPresence.UNKNOWN }
                    ?: localPresence,
                consumedKeys = consumedKeys.filterTo(linkedSetOf()) { it in nextKeys },
                prefetchedManifest = null,
                prefetchToken = 0L,
                plan = ReaderAssetPlan.EMPTY,
            )
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
            committedManifest = manifest,
            recentCommittedManifests = committedManifest
                ?.let { previous ->
                    listOf(previous) + recentCommittedManifests.filterNot {
                        it.canonicalChapterId == previous.canonicalChapterId
                    }
                }
                ?.take(RECENT_HISTORY_DEPTH)
                .orEmpty(),
            viewport = null,
            activeProtections = ReaderAssetActiveProtections.EMPTY,
            localPresence = manifest?.descriptors
                ?.associate { it.key to ReaderAssetLocalPresence.UNKNOWN }
                .orEmpty(),
            consumedKeys = emptySet(),
            prefetchedManifest = null,
            prefetchToken = 0L,
            plan = ReaderAssetPlan.EMPTY,
        )
    }

    private companion object {
        const val RECENT_HISTORY_DEPTH = 2
    }
}

data class ReaderPrefetchedDocumentArtifact(
    val sessionId: ReaderSessionId,
    val prefetchToken: Long,
    val graphRevision: ReaderAssetGraphRevision,
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
