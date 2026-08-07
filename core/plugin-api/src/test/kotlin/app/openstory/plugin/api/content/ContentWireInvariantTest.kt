package app.openstory.plugin.api.content

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ContentWireInvariantTest {
    @Test
    fun syncDeltaRejectsDuplicateAndOverlappingReleaseIds() {
        val release = release("release-1")
        assertFailsWith<IllegalArgumentException> {
            ChapterSyncDelta(
                upserts = listOf(release, release),
                tombstoneSourceReleaseIds = emptySet(),
                nextCursor = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ChapterSyncDelta(
                upserts = listOf(release),
                tombstoneSourceReleaseIds = setOf("release-1"),
                nextCursor = null,
            )
        }
    }

    @Test
    fun syncDeltaRejectsWhitespaceInsideTombstoneId() {
        assertFailsWith<IllegalArgumentException> {
            ChapterSyncDelta(
                upserts = emptyList(),
                tombstoneSourceReleaseIds = setOf("release 1"),
                nextCursor = null,
            )
        }
    }

    @Test
    fun syncDeltaRejectsBlankCursor() {
        assertFailsWith<IllegalArgumentException> {
            ChapterSyncDelta(
                upserts = emptyList(),
                tombstoneSourceReleaseIds = emptySet(),
                nextCursor = " ",
            )
        }
    }

    @Test
    fun chapterHeadingRequiresSupportedLevel() {
        assertFailsWith<IllegalArgumentException> {
            ChapterBlock.Heading(
                level = 0,
                text = ChapterText("Heading"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ChapterBlock.Heading(
                level = 7,
                text = ChapterText("Heading"),
            )
        }
    }

    @Test
    fun chapterTextSpansMustStayWithinTextAndHavePositiveRange() {
        assertFailsWith<IllegalArgumentException> {
            ChapterText(
                value = "short",
                spans = listOf(
                    ChapterTextSpan(
                        start = 0,
                        endExclusive = 10,
                        style = ChapterTextStyle.STRONG,
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ChapterText(
                value = "abcdef",
                spans = listOf(
                    ChapterTextSpan(4, 4, ChapterTextStyle.EMPHASIS),
                ),
            )
        }
    }

    @Test
    fun chapterImageRequiresHttpsUrlAndMatchingHost() {
        assertFailsWith<IllegalArgumentException> {
            ChapterImageReference(
                url = "https://cdn.example/image.webp",
                declaredHost = "images.example",
            )
        }
    }

    private fun release(id: String) = SourceChapterRelease(
        sourceReleaseId = id,
        sourceUrl = "https://content.example/chapter/$id",
        languageTag = "vi",
        rawTitle = "Chapter",
        rawVolume = null,
        rawChapter = null,
        rawPart = null,
        kindHint = ChapterKindHint.UNKNOWN,
        normalizedVolumeHint = null,
        normalizedChapterHint = null,
        normalizedPartHint = null,
        normalizedTitleHint = null,
        translatorOrUploader = null,
        publishedAtEpochMillis = null,
        updatedAtEpochMillis = null,
        contentFingerprint = null,
    )
}
