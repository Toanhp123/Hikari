package app.openstory.reader.ui

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.assets.ReaderCommittedAssetManifestSnapshot
import app.openstory.reader.assets.ReaderAssetChapterManifest
import app.openstory.reader.assets.ReaderPageAssetRequest
import app.openstory.reader.routing.ReaderSessionId
import app.openstory.reader.document.ReaderDocument

data class ReaderAssetUiState(
    val manifest: ReaderAssetChapterManifest,
    val manifestRevision: Long,
) {
    init {
        require(manifestRevision > 0L) { "Reader asset manifest revision must be positive." }
    }

    private val requestsByBlockId: Map<String, ReaderPageAssetRequest> = manifest.descriptors.associate { descriptor ->
        descriptor.uiBlockId to ReaderPageAssetRequest(
            sessionId = manifest.sessionId,
            manifestRevision = manifestRevision,
            descriptor = descriptor,
        )
    }.also { requests ->
        require(requests.size == manifest.descriptors.size) {
            "Reader asset manifest UI block IDs must be unique."
        }
    }

    fun requestForBlockId(blockId: String): ReaderPageAssetRequest? = requestsByBlockId[blockId]
}

internal fun ReaderCommittedAssetManifestSnapshot.toReaderAssetUiStateIfCurrent(
    activeSessionId: ReaderSessionId,
    activeChapterId: CanonicalChapterId,
    activeReleaseId: ChapterReleaseId,
    currentManifestRevision: Long?,
): ReaderAssetUiState? {
    if (sessionId != activeSessionId || manifest.sessionId != activeSessionId) return null
    if (manifest.canonicalChapterId != activeChapterId) return null
    if (manifest.selectedReleaseId != activeReleaseId) return null
    if (currentManifestRevision != null && manifestRevision <= currentManifestRevision) return null
    return ReaderAssetUiState(manifest, manifestRevision)
}

data class ReaderUiState(
    val loading: Boolean = true,
    val committedChapterId: CanonicalChapterId? = null,
    val transitionTargetChapterId: CanonicalChapterId? = null,
    val transitionTargetReleaseId: ChapterReleaseId? = null,
    val chapterLabel: String = "",
    val document: ReaderDocument? = null,
    val assets: ReaderAssetUiState? = null,
    val releases: List<ReaderReleaseUiModel> = emptyList(),
    val selectedReleaseId: ChapterReleaseId? = null,
    val previousChapterId: CanonicalChapterId? = null,
    val nextChapterId: CanonicalChapterId? = null,
    val restoredBlockId: String? = null,
    val restoredCharacterOffset: Int = 0,
    val restoredProgressFraction: Float = 0f,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val availableOffline: Boolean = false,
    val failure: String? = null,
    val failureRetryable: Boolean = true,
    val preferenceFailure: String? = null,
)

data class ReaderReleaseUiModel(
    val id: ChapterReleaseId,
    val label: String,
    val source: String,
    val languageTag: String,
)

data class ReaderAssistedArgs(
    val storyId: String,
    val chapterId: String,
    val releaseId: String?,
)

internal const val MIN_FONT_SCALE = 0.8f
internal const val MAX_FONT_SCALE = 1.6f
internal const val FONT_SCALE_STEP = 0.1f
private const val DEFAULT_FONT_SCALE = 1f
