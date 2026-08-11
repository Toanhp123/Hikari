package app.openstory.chapters.aggregation

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ParsedChapterLabel

data class ChapterMatchScore(
    val score: Double,
    val explicitConflict: Boolean,
)

class ChapterMatchScorer {
    fun compare(left: ParsedChapterLabel, right: ParsedChapterLabel): ChapterMatchScore {
        val explicitConflict = left.conflictsWith(right)
        val score = when {
            explicitConflict -> NO_MATCH_SCORE
            left.hasExactIdentity(right) -> EXACT_MATCH_SCORE
            left.hasSameTitle(right) && left.kind == right.kind -> SAME_KIND_TITLE_SCORE
            left.hasSameTitle(right) -> TITLE_ONLY_SCORE
            else -> NO_MATCH_SCORE
        }
        return ChapterMatchScore(score, explicitConflict)
    }
}

private fun ParsedChapterLabel.conflictsWith(other: ParsedChapterLabel): Boolean {
    val numberConflict = chapter != null && other.chapter != null && chapter != other.chapter
    val kindConflict = kind != ChapterKind.UNKNOWN &&
        other.kind != ChapterKind.UNKNOWN &&
        kind != other.kind
    return numberConflict || kindConflict
}

private fun ParsedChapterLabel.hasExactIdentity(other: ParsedChapterLabel): Boolean =
    kind == other.kind &&
        volume == other.volume &&
        chapter == other.chapter &&
        part == other.part &&
        (chapter != null || kind != ChapterKind.UNKNOWN)

private fun ParsedChapterLabel.hasSameTitle(other: ParsedChapterLabel): Boolean =
    normalizedTitle != null && normalizedTitle == other.normalizedTitle

private const val NO_MATCH_SCORE = 0.0
private const val TITLE_ONLY_SCORE = 0.65
private const val SAME_KIND_TITLE_SCORE = 0.75
private const val EXACT_MATCH_SCORE = 1.0
