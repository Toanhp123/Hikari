package app.openstory.plugin.api.content

import app.openstory.common.AppResult
import app.openstory.model.ContentType
import app.openstory.plugin.api.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ContentContractTest {

    @Test
    fun syncDeltaCannotDeleteUnknownBlankId() {
        assertFailsWith<IllegalArgumentException> {
            ChapterSyncDelta(
                upserts = emptyList(),
                tombstoneSourceReleaseIds = setOf(" "),
                nextCursor = "c2",
            )
        }
    }

    @Test
    fun contentPluginExposesSearchStoryAndChapterOperations() {
        val plugin: ContentPlugin = object : ContentPlugin {
            override suspend fun search(
                request: ContentSearchRequest,
            ): AppResult<Page<ContentStoryCandidate>> =
                TODO("Contract-only fixture")

            override suspend fun story(
                sourceStoryId: String,
            ): AppResult<ContentStoryDetails> =
                TODO("Contract-only fixture")

            override suspend fun latest(
                sourceStoryId: String,
                limit: Int,
            ): AppResult<List<SourceChapterRelease>> =
                TODO("Contract-only fixture")

            override suspend fun allChapters(
                sourceStoryId: String,
            ): AppResult<List<SourceChapterRelease>> =
                TODO("Contract-only fixture")

            override suspend fun sync(
                sourceStoryId: String,
                cursor: String?,
            ): AppResult<ChapterSyncDelta> =
                TODO("Contract-only fixture")

            override suspend fun chapter(
                sourceReleaseId: String,
            ): AppResult<ChapterDocument> =
                TODO("Contract-only fixture")
        }

        assertNotNull(plugin)
    }

    @Test
    fun chapterReleaseRetainsRawFieldsAndNormalizedHints() {
        val release = SourceChapterRelease(
            sourceReleaseId = "release::opaque::10.5",
            sourceUrl = "https://example.com/chapter/10-5",
            languageTag = "vi",
            rawTitle = "Chương 010.5 — Ngoại truyện",
            rawVolume = "Volume 02",
            rawChapter = "010.5",
            rawPart = null,
            kindHint = ChapterKindHint.SIDE_STORY,
            normalizedVolumeHint = "2",
            normalizedChapterHint = "10.5",
            normalizedPartHint = null,
            normalizedTitleHint = "ngoai truyen",
            translatorOrUploader = "Nhóm dịch",
            publishedAtEpochMillis = null,
            updatedAtEpochMillis = null,
            contentFingerprint = null,
        )

        assertEquals("010.5", release.rawChapter)
        assertEquals("10.5", release.normalizedChapterHint)
        assertEquals(ChapterKindHint.SIDE_STORY, release.kindHint)
    }

    @Test
    fun chapterDocumentUsesStructuredBlocks() {
        val document = ChapterDocument(
            title = "Chapter 1",
            blocks = listOf(
                ChapterBlock.Paragraph(
                    text = ChapterText(
                        value = "Opening paragraph.",
                        spans = listOf(
                            ChapterTextSpan(
                                start = 0,
                                endExclusive = 7,
                                style = ChapterTextStyle.EMPHASIS,
                            ),
                        ),
                    ),
                ),
                ChapterBlock.Heading(
                    level = 2,
                    text = ChapterText(value = "Section"),
                ),
                ChapterBlock.Divider,
                ChapterBlock.Image(
                    reference = ChapterImageReference(
                        url = "https://images.example/chapter-1.webp",
                        declaredHost = "images.example",
                    ),
                    altText = "Illustration",
                ),
                ChapterBlock.Note(
                    text = ChapterText(value = "Translator note"),
                ),
            ),
        )

        assertEquals(5, document.blocks.size)
    }

    @Test
    fun contentStoryDetailsMayCarryDirectCatalogMappings() {
        val details = ContentStoryDetails(
            sourceStoryId = "story::opaque",
            sourceUrl = "https://example.com/story",
            title = "Example Story",
            aliases = emptyList(),
            authors = listOf("Author"),
            description = null,
            contentType = ContentType.WEB_NOVEL,
            languageTags = setOf("vi"),
            directCatalogMappings = listOf(
                DirectCatalogMapping(
                    catalogPluginId = "community.catalog",
                    catalogSourceId = "catalog::opaque",
                ),
            ),
        )

        assertEquals(
            "catalog::opaque",
            details.directCatalogMappings.single().catalogSourceId,
        )
    }
}
