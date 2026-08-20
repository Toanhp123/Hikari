package app.openstory.chapters.sync

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.normalization.ChapterLabelParser
import app.openstory.chapters.source.ChapterSourceRelease

internal data class NormalizedChapterSourceRelease(
    val displayLabel: String,
    val parsedLabel: ParsedChapterLabel,
)

internal class ChapterSourceReleaseNormalizer(
    private val parser: ChapterLabelParser,
) {
    fun normalize(sourceRelease: ChapterSourceRelease): NormalizedChapterSourceRelease {
        val rawNumber = sourceRelease.rawNumber?.trim()?.takeIf(String::isNotBlank)
        val sourceTitle = sourceRelease.title?.trim()?.takeIf(String::isNotBlank)
        val parsedLabel = sourceParsedLabel(rawNumber, sourceTitle, sourceRelease.sourceReleaseId)
        return NormalizedChapterSourceRelease(
            displayLabel = sourceDisplayLabel(rawNumber, sourceTitle, sourceRelease.sourceReleaseId, parsedLabel),
            parsedLabel = parsedLabel,
        )
    }

    private fun sourceParsedLabel(
        rawNumber: String?,
        sourceTitle: String?,
        fallback: String,
    ): ParsedChapterLabel {
        val rawParsed = rawNumber?.let(parser::parse)
        if (rawParsed != null && rawParsed.hasSourceIdentity()) {
            val titleParsed = sourceTitle?.let(parser::parse)
            val titleIsIdentityOnly = titleParsed != null &&
                titleParsed.kind == rawParsed.kind &&
                titleParsed.volume == rawParsed.volume &&
                titleParsed.chapter == rawParsed.chapter &&
                titleParsed.part == rawParsed.part &&
                titleParsed.normalizedTitle == null
            val normalizedTitle = if (sourceTitle == null || titleIsIdentityOnly) {
                null
            } else {
                parser.parse("$rawNumber - $sourceTitle").normalizedTitle
                    ?: titleParsed?.normalizedTitle
            }
            return rawParsed.copy(normalizedTitle = normalizedTitle)
        }
        return parser.parse(sourceTitle ?: rawNumber ?: fallback)
    }

    private fun sourceDisplayLabel(
        rawNumber: String?,
        sourceTitle: String?,
        fallback: String,
        parsedLabel: ParsedChapterLabel,
    ): String {
        if (rawNumber == null) return sourceTitle ?: fallback
        val primary = parsedLabel.chapter?.let { chapter ->
            buildString {
                append("Chapter ")
                append(chapter.stripTrailingZeros().toPlainString())
                parsedLabel.part?.let { part -> append(" · Part ").append(part) }
            }
        } ?: rawNumber
        val title = sourceTitle?.takeUnless { candidate ->
            val titleLabel = parser.parse(candidate)
            titleLabel.kind == parsedLabel.kind &&
                titleLabel.chapter == parsedLabel.chapter &&
                titleLabel.part == parsedLabel.part &&
                titleLabel.normalizedTitle == null
        }
        return if (title == null) primary else "$primary · $title"
    }
}

private fun ParsedChapterLabel.hasSourceIdentity(): Boolean =
    chapter != null || kind != ChapterKind.UNKNOWN
