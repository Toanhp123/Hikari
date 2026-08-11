package app.openstory.chapters.normalization

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ParsedChapterLabel
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

class ChapterLabelParser {
    fun parse(label: String): ParsedChapterLabel {
        val normalized = label.normalizedText()
        if (normalized.isEmpty()) return unknown(null)

        val namedKind = NAMED_KINDS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(normalized) }
        val volume = VOLUME.find(normalized)?.groupValues?.get(1)?.toDecimal()
        val explicitChapter = CHAPTER.find(normalized)?.groupValues?.get(1)?.toDecimal()
        val bareMatch = if (explicitChapter == null && namedKind == null) {
            BARE_NUMBER.matchEntire(normalized)
        } else {
            null
        }
        val bareChapter = bareMatch?.groupValues?.get(1)?.toDecimal()
        val part = PART.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        val kind = namedKind?.second ?: if (explicitChapter != null || bareChapter != null) {
            ChapterKind.NUMBERED
        } else {
            ChapterKind.UNKNOWN
        }
        val title = if (bareMatch != null) {
            bareMatch.groupValues.getOrNull(2)?.cleanTitle()
        } else {
            normalized
                .removeMatch(VOLUME)
                .removeMatch(CHAPTER)
                .removeMatch(PART)
                .let { value -> namedKind?.first?.replace(value, " ") ?: value }
                .cleanTitle()
        }

        return ParsedChapterLabel(kind, volume, explicitChapter ?: bareChapter, part, title)
    }

    private fun unknown(title: String?) = ParsedChapterLabel(
        kind = ChapterKind.UNKNOWN,
        volume = null,
        chapter = null,
        part = null,
        normalizedTitle = title,
    )
}

private fun String.normalizedText(): String = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    .replace(COMBINING_MARKS, "")
    .lowercase(Locale.ROOT)
    .replace(WHITESPACE, " ")
    .trim()

private fun String.removeMatch(regex: Regex): String = regex.replace(this, " ")

private fun String.cleanTitle(): String? = trim(' ', '-', ':', '.', '_')
    .replace(WHITESPACE, " ")
    .trim()
    .ifEmpty { null }

private fun String.toDecimal(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()

private val COMBINING_MARKS = Regex("\\p{M}+")
private val WHITESPACE = Regex("\\s+")
private const val DECIMAL = "([0-9]+(?:\\.[0-9]+)?)"
private val VOLUME = Regex("(?:^|\\s)(?:vol(?:ume)?|v|tap)\\.?\\s*$DECIMAL", RegexOption.IGNORE_CASE)
private val CHAPTER = Regex("(?:^|\\s)(?:ch(?:apter)?|chap|chuong)\\.?\\s*$DECIMAL", RegexOption.IGNORE_CASE)
private val PART = Regex("(?:^|\\s)(?:part|pt|phan)\\.?\\s*([0-9]+)", RegexOption.IGNORE_CASE)
private val BARE_NUMBER = Regex("^$DECIMAL(?:\\s*[-:]\\s*(.*))?$")
private val NAMED_KINDS = listOf(
    Regex("(?:^|\\s)prologue(?:\\s+[0-9]+)?", RegexOption.IGNORE_CASE) to ChapterKind.PROLOGUE,
    Regex("(?:^|\\s)epilogue(?:\\s+[0-9]+)?", RegexOption.IGNORE_CASE) to ChapterKind.EPILOGUE,
    Regex("(?:^|\\s)side\\s*story(?:\\s+[0-9]+)?", RegexOption.IGNORE_CASE) to ChapterKind.SIDE_STORY,
    Regex("(?:^|\\s)extra(?:\\s+[0-9]+)?", RegexOption.IGNORE_CASE) to ChapterKind.EXTRA,
    Regex("(?:^|\\s)special(?:\\s+[0-9]+)?", RegexOption.IGNORE_CASE) to ChapterKind.SPECIAL,
)
