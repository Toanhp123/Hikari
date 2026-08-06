package app.openstory.model

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingProgressTest {

    @Test
    fun progressRetainsCanonicalAndReleaseIdentity() {
        val progress = ReadingProgress(
            storyId = StoryId("s1"),
            chapterId = ChapterId("c100"),
            releaseId = ReleaseId("sourceA:100"),
            position = ReaderPosition.Paragraph(
                index = 12,
                fraction = 0.45f,
            ),
            completed = true,
            updatedAtEpochMillis = 1_000L,
        )

        assertEquals(
            ChapterId("c100"),
            progress.chapterId,
        )
        assertEquals(
            ReleaseId("sourceA:100"),
            progress.releaseId,
        )
    }

    @Test
    fun releasesFromDifferentSourcesShareCanonicalChapter() {
        val chapterId = ChapterId("chapter:10.5")

        val first = ChapterRelease(
            id = ReleaseId("source-a:10.5"),
            chapterId = chapterId,
            contentMappingId = ContentMappingId("mapping-a"),
            pluginId = PluginId("source-a"),
            externalReleaseId = "10.5",
            sourceUrl = "https://source-a.example/chapter/10.5",
            language = LanguageTag("EN"),
            title = "Chapter 10.5",
            volumeNumber = BigDecimal("1"),
            chapterNumber = BigDecimal("10.5"),
            partNumber = null,
            translatorOrUploader = "Group A",
            publishedAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_100L,
            contentFingerprint = "sha256:first",
            availability = ReleaseAvailability.AVAILABLE,
            fetchedAtEpochMillis = 1_200L,
        )

        val second = ChapterRelease(
            id = ReleaseId("source-b:chapter-10-5"),
            chapterId = chapterId,
            contentMappingId = ContentMappingId("mapping-b"),
            pluginId = PluginId("source-b"),
            externalReleaseId = "chapter-10-5",
            sourceUrl = "https://source-b.example/releases/chapter-10-5",
            language = LanguageTag("VI"),
            title = "Chương 10.5",
            volumeNumber = BigDecimal("1"),
            chapterNumber = BigDecimal("10.5"),
            partNumber = null,
            translatorOrUploader = "Group B",
            publishedAtEpochMillis = 1_300L,
            updatedAtEpochMillis = null,
            contentFingerprint = "sha256:second",
            availability = ReleaseAvailability.AVAILABLE,
            fetchedAtEpochMillis = 1_400L,
        )

        assertEquals(chapterId, first.chapterId)
        assertEquals(chapterId, second.chapterId)
        assertNotEquals(first.pluginId, second.pluginId)
        assertNotEquals(
            first.externalReleaseId,
            second.externalReleaseId,
        )
    }

    @Test
    fun specialChapterUsesSemanticKindWithoutForcedNumber() {
        val chapter = CanonicalChapter(
            id = ChapterId("chapter:prologue"),
            storyId = StoryId("story:1"),
            kind = ChapterKind.PROLOGUE,
            volumeNumber = null,
            chapterNumber = null,
            partNumber = null,
            normalizedTitle = "prologue",
            sortKey = "0000:prologue",
            firstKnownPublishedAtEpochMillis = 1_000L,
        )

        assertEquals(ChapterKind.PROLOGUE, chapter.kind)
        assertNull(chapter.chapterNumber)
    }

    @Test
    fun contentMappingRetainsUserLockedCorrection() {
        val mapping = ContentMapping(
            id = ContentMappingId("mapping:1"),
            storyId = StoryId("story:1"),
            pluginId = PluginId("source-a"),
            externalStoryId = "novel-123",
            sourceUrl = "https://source-a.example/novel-123",
            language = LanguageTag("EN"),
            origin = MappingOrigin.USER,
            confidence = 1.0,
            userLocked = true,
            enabled = true,
            lastSuccessfulSyncAtEpochMillis = null,
            nextEligibleSyncAtEpochMillis = null,
            failureState = null,
        )

        assertEquals(MappingOrigin.USER, mapping.origin)
        assertTrue(mapping.userLocked)
    }
}
