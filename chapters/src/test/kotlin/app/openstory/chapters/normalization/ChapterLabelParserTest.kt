package app.openstory.chapters.normalization

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ParsedChapterLabel
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterLabelParserTest {
    private val parser = ChapterLabelParser()

    @Test
    fun parsesVolumeDecimalChapterPartAndTitle() {
        assertEquals(
            ParsedChapterLabel(
                kind = ChapterKind.NUMBERED,
                volume = BigDecimal("2"),
                chapter = BigDecimal("10.5"),
                part = 3,
                normalizedTitle = "the eclipse",
            ),
            parser.parse("Vol. 2 Ch. 10.5 Part 3 - The Eclipse"),
        )
    }

    @Test
    fun recognizesNamedChapterKinds() {
        val cases = mapOf(
            "Prologue - Arrival" to ChapterKind.PROLOGUE,
            "Epilogue: Dawn" to ChapterKind.EPILOGUE,
            "Side Story 2 - Camp" to ChapterKind.SIDE_STORY,
            "Extra 4" to ChapterKind.EXTRA,
            "Special: Festival" to ChapterKind.SPECIAL,
        )

        cases.forEach { (label, kind) ->
            assertEquals(kind, parser.parse(label).kind, label)
        }
    }

    @Test
    fun parsesLocalizedVolumeAndChapterPrefixes() {
        assertEquals(
            ParsedChapterLabel(
                kind = ChapterKind.NUMBERED,
                volume = BigDecimal("3"),
                chapter = BigDecimal("12"),
                part = null,
                normalizedTitle = "khoi dau",
            ),
            parser.parse("Tap 3 Chuong 12: Khoi dau"),
        )
    }

    @Test
    fun parsesBareDecimalChapterAndKeepsTitle() {
        assertEquals(
            ParsedChapterLabel(
                kind = ChapterKind.NUMBERED,
                volume = null,
                chapter = BigDecimal("12.5"),
                part = null,
                normalizedTitle = "interlude",
            ),
            parser.parse("12.5 - Interlude"),
        )
    }

    @Test
    fun malformedOrBlankLabelsRemainUnknown() {
        assertEquals(
            ParsedChapterLabel(ChapterKind.UNKNOWN, null, null, null, "chapter twelve"),
            parser.parse("Chapter twelve"),
        )
        assertEquals(
            ParsedChapterLabel(ChapterKind.UNKNOWN, null, null, null, null),
            parser.parse("  "),
        )
    }
}
