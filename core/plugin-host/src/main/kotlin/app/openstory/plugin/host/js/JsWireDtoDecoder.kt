package app.openstory.plugin.host.js

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogSection
import app.openstory.plugin.api.content.ChapterDocument
import app.openstory.plugin.api.content.ChapterSyncDelta
import app.openstory.plugin.api.content.ContentStoryCandidate
import app.openstory.plugin.api.content.ContentStoryDetails
import app.openstory.plugin.api.content.SourceChapterRelease
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class JsWireDtoDecoder(
    private val validator: PluginWireDtoValidator,
) {
    fun decodeCatalogHome(source: String): AppResult<List<CatalogSection>> = decode(
        source,
        serializer<List<CatalogSection>>(),
        validator::validateCatalogHome,
    )

    fun decodeCatalogSearch(source: String): AppResult<Page<CatalogCard>> = decode(
        source,
        serializer<Page<CatalogCard>>(),
        validator::validateCatalogSearch,
    )

    fun decodeCatalogDetails(source: String): AppResult<CatalogDetails> = decode(
        source,
        serializer<CatalogDetails>(),
        validator::validateCatalogDetails,
    )

    fun decodeCatalogFilters(source: String): AppResult<List<CatalogFilterDefinition>> = decode(
        source,
        serializer<List<CatalogFilterDefinition>>(),
        validator::validateCatalogFilters,
    )

    fun decodeContentSearch(source: String): AppResult<Page<ContentStoryCandidate>> = decode(
        source,
        serializer<Page<ContentStoryCandidate>>(),
        validator::validateContentSearch,
    )

    fun decodeContentStory(source: String): AppResult<ContentStoryDetails> = decode(
        source,
        serializer<ContentStoryDetails>(),
        validator::validateContentStory,
    )

    fun decodeReleases(source: String): AppResult<List<SourceChapterRelease>> = decode(
        source,
        serializer<List<SourceChapterRelease>>(),
        validator::validateReleases,
    )

    fun decodeChapterSync(source: String): AppResult<ChapterSyncDelta> = decode(
        source,
        serializer<ChapterSyncDelta>(),
        validator::validateChapterSyncDelta,
    )

    fun decodeChapterDocument(source: String): AppResult<ChapterDocument> = decode(
        source,
        serializer<ChapterDocument>(),
        validator::validateChapterDocument,
    )

    private fun <T> decode(
        source: String,
        serializer: KSerializer<T>,
        validate: (T) -> AppResult<T>,
    ): AppResult<T> = try {
        validate(JSON.decodeFromString(serializer, source))
    } catch (_: IllegalArgumentException) {
        invalidOutput()
    }

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }
    }
}

private fun invalidOutput(): AppResult.Failure = AppResult.Failure(
    AppError.Plugin(
        code = "plugin.javascript_output_invalid",
        retryable = false,
    ),
)
