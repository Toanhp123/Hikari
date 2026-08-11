package app.openstory.chapters.model

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import java.math.BigDecimal

enum class ChapterKind {
    NUMBERED,
    PROLOGUE,
    EPILOGUE,
    SIDE_STORY,
    EXTRA,
    SPECIAL,
    UNKNOWN,
}

data class ParsedChapterLabel(
    val kind: ChapterKind,
    val volume: BigDecimal?,
    val chapter: BigDecimal?,
    val part: Int?,
    val normalizedTitle: String?,
)

data class CanonicalChapter(
    val id: CanonicalChapterId,
    val storyId: StoryId,
    val parsedLabel: ParsedChapterLabel,
    val displayLabel: String,
    val tombstoned: Boolean,
    val releaseIds: Set<ChapterReleaseId> = emptySet(),
)

data class ChapterRelease(
    val id: ChapterReleaseId,
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceStoryId: String,
    val sourceReleaseId: String,
    val displayLabel: String,
    val parsedLabel: ParsedChapterLabel,
    val languageTag: String,
    val publishedAtEpochMillis: Long?,
    val canonicalChapterId: CanonicalChapterId?,
)

enum class ChapterOverrideKind {
    FORCE_LINK,
    FORCE_SEPARATE,
}

data class ChapterAggregationOverride(
    val releaseId: ChapterReleaseId,
    val canonicalChapterId: CanonicalChapterId?,
    val kind: ChapterOverrideKind,
)
