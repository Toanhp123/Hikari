package app.openstory.plugin.api.content

import app.openstory.common.AppResult
import app.openstory.plugin.api.Page

interface ContentPlugin {
    suspend fun search(
        request: ContentSearchRequest,
    ): AppResult<Page<ContentStoryCandidate>>

    suspend fun story(
        sourceStoryId: String,
    ): AppResult<ContentStoryDetails>

    suspend fun latest(
        sourceStoryId: String,
        limit: Int,
    ): AppResult<List<SourceChapterRelease>>

    suspend fun allChapters(
        sourceStoryId: String,
    ): AppResult<List<SourceChapterRelease>>

    suspend fun sync(
        sourceStoryId: String,
        cursor: String?,
    ): AppResult<ChapterSyncDelta>

    suspend fun chapter(
        sourceReleaseId: String,
    ): AppResult<ChapterDocument>
}
