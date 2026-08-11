package app.openstory.reader.ui

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.document.ReaderDocument

data class ReaderUiState(
    val loading: Boolean = true,
    val chapterLabel: String = "",
    val document: ReaderDocument? = null,
    val releases: List<ReaderReleaseUiModel> = emptyList(),
    val selectedReleaseId: ChapterReleaseId? = null,
    val previousChapterId: CanonicalChapterId? = null,
    val nextChapterId: CanonicalChapterId? = null,
    val restoredBlockId: String? = null,
    val restoredCharacterOffset: Int = 0,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val availableOffline: Boolean = false,
    val failure: String? = null,
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
