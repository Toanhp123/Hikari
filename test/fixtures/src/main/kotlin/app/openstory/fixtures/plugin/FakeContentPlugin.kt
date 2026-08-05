package app.openstory.fixtures.plugin

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.model.ContentType
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.content.ChapterBlock
import app.openstory.plugin.api.content.ChapterDocument
import app.openstory.plugin.api.content.ChapterKindHint
import app.openstory.plugin.api.content.ChapterSyncDelta
import app.openstory.plugin.api.content.ChapterText
import app.openstory.plugin.api.content.ContentPlugin
import app.openstory.plugin.api.content.ContentSearchRequest
import app.openstory.plugin.api.content.ContentStoryCandidate
import app.openstory.plugin.api.content.ContentStoryDetails
import app.openstory.plugin.api.content.SourceChapterRelease

enum class FakeContentMode {
    NORMAL,
    DUPLICATE_SEARCH_IDS,
    OVERSIZED_SEARCH_PAGE,
    INVALID_LANGUAGE_TAG,
    UNDECLARED_STORY_HOST,
    BLANK_CHAPTER_CONTENT,
    MALFORMED_SYNC_CURSOR,
    TIMEOUT,
    RATE_LIMIT,
    MISSING_TIMESTAMP,
    DELETED_RELEASE,
    DUPLICATE_CHAPTER,
    SPECIAL_CHAPTER,
}

class FakeContentPlugin(
    private val mode: FakeContentMode =
        FakeContentMode.NORMAL,
) : ContentPlugin {

    override suspend fun search(
        request: ContentSearchRequest,
    ): AppResult<Page<ContentStoryCandidate>> =
        when (mode) {
            FakeContentMode.TIMEOUT ->
                AppResult.Failure(
                    AppError.Network(
                        code = "network.timeout",
                        retryable = true,
                    ),
                )

            FakeContentMode.RATE_LIMIT ->
                AppResult.Failure(
                    AppError.Network(
                        code = "network.rate_limited",
                        retryable = true,
                    ),
                )

            else ->
                AppResult.Success(
                    Page(
                        items = searchItems(),
                        nextToken = null,
                    ),
                )
        }
    override suspend fun story(
        sourceStoryId: String,
    ): AppResult<ContentStoryDetails> {
        val sourceUrl =
            if (
                mode ==
                FakeContentMode.UNDECLARED_STORY_HOST
            ) {
                "https://undeclared.example/story/$sourceStoryId"
            } else {
                "https://fixture.example/story/$sourceStoryId"
            }

        return AppResult.Success(
            ContentStoryDetails(
                sourceStoryId = sourceStoryId,
                sourceUrl = sourceUrl,
                title = "Deterministic Fixture Story",
                aliases = emptyList(),
                authors = listOf("Fixture Author"),
                description =
                    "Deterministic content plugin fixture.",
                contentType = ContentType.WEB_NOVEL,
                languageTags = setOf("en"),
            ),
        )
    }

    override suspend fun latest(
        sourceStoryId: String,
        limit: Int,
    ): AppResult<List<SourceChapterRelease>> =
        AppResult.Success(
            listOf(
                if (
                    mode ==
                    FakeContentMode.MISSING_TIMESTAMP
                ) {
                    release(
                        publishedAtEpochMillis = null,
                        updatedAtEpochMillis = null,
                    )
                } else {
                    release()
                },
            ).take(limit),
        )

    override suspend fun allChapters(
        sourceStoryId: String,
    ): AppResult<List<SourceChapterRelease>> =
        AppResult.Success(
            when (mode) {
                FakeContentMode.DUPLICATE_CHAPTER ->
                    listOf(
                        release(
                            sourceReleaseId =
                                "fixture-release-duplicate-a",
                        ),
                        release(
                            sourceReleaseId =
                                "fixture-release-duplicate-b",
                        ),
                    )

                FakeContentMode.SPECIAL_CHAPTER ->
                    listOf(
                        release(
                            sourceReleaseId =
                                "fixture-release-prologue",
                            rawTitle = "Prologue",
                            kindHint =
                                ChapterKindHint.PROLOGUE,
                            normalizedVolumeHint = null,
                            normalizedChapterHint = null,
                            normalizedPartHint = null,
                            normalizedTitleHint =
                                "prologue",
                        ),
                    )

                else ->
                    listOf(release())
            },
        )

    override suspend fun sync(
        sourceStoryId: String,
        cursor: String?,
    ): AppResult<ChapterSyncDelta> =
        AppResult.Success(
            ChapterSyncDelta(
                upserts =
                    if (
                        mode ==
                        FakeContentMode.DELETED_RELEASE
                    ) {
                        emptyList()
                    } else {
                        listOf(release())
                    },
                tombstoneSourceReleaseIds =
                    if (
                        mode ==
                        FakeContentMode.DELETED_RELEASE
                    ) {
                        setOf("fixture-release-deleted")
                    } else {
                        emptySet()
                    },
                nextCursor =
                    if (
                        mode ==
                        FakeContentMode.MALFORMED_SYNC_CURSOR
                    ) {
                        "malformed cursor"
                    } else {
                        "cursor-1"
                    },
            ),
        )

    override suspend fun chapter(
        sourceReleaseId: String,
    ): AppResult<ChapterDocument> =
        AppResult.Success(
            if (
                mode ==
                FakeContentMode.BLANK_CHAPTER_CONTENT
            ) {
                ChapterDocument(
                    title = "Blank chapter fixture",
                    blocks = listOf(
                        ChapterBlock.Paragraph(
                            ChapterText("   "),
                        ),
                    ),
                )
            } else {
                ChapterDocument(
                    title = "Chapter 1",
                    blocks = listOf(
                        ChapterBlock.Paragraph(
                            ChapterText(
                                "Deterministic fixture chapter body.",
                            ),
                        ),
                    ),
                )
            },
        )

    private fun searchItems():
        List<ContentStoryCandidate> =
        when (mode) {
            FakeContentMode.DUPLICATE_SEARCH_IDS ->
                listOf(
                    candidate(
                        sourceStoryId =
                            DEFAULT_SOURCE_STORY_ID,
                    ),
                    candidate(
                        sourceStoryId =
                            DEFAULT_SOURCE_STORY_ID,
                    ),
                )

            FakeContentMode.OVERSIZED_SEARCH_PAGE ->
                (1..101).map { index ->
                    candidate(
                        sourceStoryId =
                            "fixture-story-$index",
                    )
                }

            FakeContentMode.INVALID_LANGUAGE_TAG ->
                listOf(
                    candidate(
                        languageTags =
                            setOf("not a language tag"),
                    ),
                )

            else ->
                listOf(candidate())
        }
    private fun candidate(
        sourceStoryId: String =
            DEFAULT_SOURCE_STORY_ID,
        languageTags: Set<String> = setOf("en"),
    ): ContentStoryCandidate =
        ContentStoryCandidate(
            sourceStoryId = sourceStoryId,
            sourceUrl =
                "https://fixture.example/story/$sourceStoryId",
            title = "Deterministic Fixture Story",
            authors = listOf("Fixture Author"),
            contentType = ContentType.WEB_NOVEL,
            languageTags = languageTags,
        )

    private fun release(
        sourceReleaseId: String =
            DEFAULT_SOURCE_RELEASE_ID,
        rawTitle: String = "Chapter 1",
        kindHint: ChapterKindHint =
            ChapterKindHint.NUMBERED,
        normalizedVolumeHint: String? = "1",
        normalizedChapterHint: String? = "1",
        normalizedPartHint: String? = null,
        normalizedTitleHint: String? =
            "chapter 1",
        publishedAtEpochMillis: Long? =
            1_700_000_000_000L,
        updatedAtEpochMillis: Long? =
            1_700_000_000_000L,
    ): SourceChapterRelease =
        SourceChapterRelease(
            sourceReleaseId = sourceReleaseId,
            sourceUrl =
                "https://fixture.example/chapter/$sourceReleaseId",
            languageTag = "en",
            rawTitle = rawTitle,
            rawVolume =
                normalizedVolumeHint
                    ?.let { "Volume $it" },
            rawChapter = normalizedChapterHint,
            rawPart = normalizedPartHint,
            kindHint = kindHint,
            normalizedVolumeHint =
                normalizedVolumeHint,
            normalizedChapterHint =
                normalizedChapterHint,
            normalizedPartHint =
                normalizedPartHint,
            normalizedTitleHint =
                normalizedTitleHint,
            translatorOrUploader =
                "Fixture Group",
            publishedAtEpochMillis =
                publishedAtEpochMillis,
            updatedAtEpochMillis =
                updatedAtEpochMillis,
            contentFingerprint =
                "fixture-content-fingerprint",
        )

    private companion object {
        const val DEFAULT_SOURCE_STORY_ID =
            "fixture-story-1"

        const val DEFAULT_SOURCE_RELEASE_ID =
            "fixture-release-1"
    }
}

class UnstableIdContentPlugin : ContentPlugin {
    private var searchCallCount = 0
    private val delegate = FakeContentPlugin()

    override suspend fun search(
        request: ContentSearchRequest,
    ): AppResult<Page<ContentStoryCandidate>> {
        searchCallCount += 1

        val sourceStoryId =
            "unstable-story-$searchCallCount"

        return AppResult.Success(
            Page(
                items = listOf(
                    ContentStoryCandidate(
                        sourceStoryId = sourceStoryId,
                        sourceUrl =
                            "https://fixture.example/story/$sourceStoryId",
                        title =
                            "Deterministic Fixture Story",
                        authors =
                            listOf("Fixture Author"),
                        contentType =
                            ContentType.WEB_NOVEL,
                        languageTags = setOf("en"),
                    ),
                ),
                nextToken = null,
            ),
        )
    }

    override suspend fun story(
        sourceStoryId: String,
    ): AppResult<ContentStoryDetails> =
        delegate.story(sourceStoryId)

    override suspend fun latest(
        sourceStoryId: String,
        limit: Int,
    ): AppResult<List<SourceChapterRelease>> =
        delegate.latest(
            sourceStoryId = sourceStoryId,
            limit = limit,
        )

    override suspend fun allChapters(
        sourceStoryId: String,
    ): AppResult<List<SourceChapterRelease>> =
        delegate.allChapters(sourceStoryId)

    override suspend fun sync(
        sourceStoryId: String,
        cursor: String?,
    ): AppResult<ChapterSyncDelta> =
        delegate.sync(
            sourceStoryId = sourceStoryId,
            cursor = cursor,
        )

    override suspend fun chapter(
        sourceReleaseId: String,
    ): AppResult<ChapterDocument> =
        delegate.chapter(sourceReleaseId)
}
