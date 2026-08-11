package app.openstory.chapters.source

import app.openstory.common.id.PluginId

enum class ChapterListMode {
    RECENT,
    FULL,
    INCREMENTAL,
}

data class ChapterSourceRequest(
    val sourceStoryId: String,
    val mode: ChapterListMode = ChapterListMode.FULL,
    val checkpoint: String? = null,
    val nextToken: String? = null,
)

data class ChapterSourceRelease(
    val sourceReleaseId: String,
    val title: String?,
    val rawNumber: String?,
    val languageTag: String?,
    val publishedAtEpochMillis: Long?,
)

data class ChapterSourcePage(
    val releases: List<ChapterSourceRelease>,
    val nextToken: String?,
)

data class ChapterSourceFailure(
    val code: String,
    val retryable: Boolean,
)

sealed interface ChapterSourceResult {
    data class Success(val page: ChapterSourcePage) : ChapterSourceResult

    data class Failure(val failure: ChapterSourceFailure) : ChapterSourceResult
}

interface ChapterSource {
    val pluginId: PluginId
    val version: String

    suspend fun chapters(request: ChapterSourceRequest): ChapterSourceResult
}
