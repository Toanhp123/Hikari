package app.openstory.plugin.host.selector.mapper

import app.openstory.common.AppResult
import app.openstory.model.ContentType
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.content.ChapterDocument
import app.openstory.plugin.api.content.ChapterKindHint
import app.openstory.plugin.api.content.ChapterSyncDelta
import app.openstory.plugin.api.content.ContentStoryCandidate
import app.openstory.plugin.api.content.ContentStoryDetails
import app.openstory.plugin.api.content.DirectCatalogMapping
import app.openstory.plugin.api.content.SourceChapterRelease
import app.openstory.plugin.api.selector.content.ChapterDocumentBinding
import app.openstory.plugin.host.selector.HtmlDocument
import app.openstory.plugin.host.selector.HtmlDocumentAdapter
import app.openstory.plugin.host.selector.binding.SelectorBindingEvaluator
import app.openstory.plugin.host.selector.binding.SelectorBoundValue
import app.openstory.plugin.host.selector.binding.SelectorEvaluationBudget
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator

class ContentSelectorMapper(
    private val outputValidator: PluginWireDtoValidator,
    urlPolicy: PluginUrlPolicy,
    html: HtmlDocumentAdapter,
    evaluator: SelectorBindingEvaluator,
) {
    private val chapterMapper = ChapterDocumentMapper(
        outputValidator,
        urlPolicy,
        html,
        evaluator,
    )

    fun mapSearch(
        items: SelectorBoundValue,
        nextToken: SelectorBoundValue?,
    ): AppResult<Page<ContentStoryCandidate>> = mapBoundOutput("items") {
        Page(
            items = BoundValueReader(items, "items").values().map(::mapCandidate),
            nextToken = optionalToken(nextToken, "nextToken"),
        )
    }.flatMap(outputValidator::validateContentSearch)

    fun mapStory(value: SelectorBoundValue): AppResult<ContentStoryDetails> =
        mapBoundOutput("story") {
            val story = BoundValueReader(value, "story")
            ContentStoryDetails(
                sourceStoryId = story.field("sourceStoryId").text(),
                sourceUrl = story.field("sourceUrl").text(),
                title = story.field("title").text(),
                aliases = story.optionalTextList("aliases"),
                authors = story.optionalTextList("authors"),
                description = story.optionalField("description")?.text(),
                contentType = ContentType.valueOf(story.field("contentType").text()),
                languageTags = story.optionalTextList("languageTags").toSet(),
                directCatalogMappings = story.optionalField("directCatalogMappings")
                    ?.values()
                    ?.map(::mapDirectCatalogMapping)
                    .orEmpty(),
            )
        }.flatMap(outputValidator::validateContentStory)

    fun mapReleases(value: SelectorBoundValue): AppResult<List<SourceChapterRelease>> =
        mapBoundOutput("releases") {
            BoundValueReader(value, "releases").values().map(::mapRelease)
        }.flatMap(outputValidator::validateReleases)

    fun mapSync(value: SelectorBoundValue): AppResult<ChapterSyncDelta> =
        mapBoundOutput("delta") {
            val delta = BoundValueReader(value, "delta")
            ChapterSyncDelta(
                upserts = delta.field("upserts").values().map(::mapRelease),
                tombstoneSourceReleaseIds = delta
                    .optionalTextList("tombstoneSourceReleaseIds")
                    .toSet(),
                nextCursor = delta.optionalField("nextCursor")?.text(),
            )
        }.flatMap(outputValidator::validateChapterSyncDelta)

    suspend fun mapChapter(
        document: HtmlDocument,
        binding: ChapterDocumentBinding,
        budget: SelectorEvaluationBudget,
    ): AppResult<ChapterDocument> = chapterMapper.map(document, binding, budget)

    private fun mapCandidate(value: BoundValueReader): ContentStoryCandidate =
        ContentStoryCandidate(
            sourceStoryId = value.field("sourceStoryId").text(),
            sourceUrl = value.optionalField("sourceUrl")?.text(),
            title = value.field("title").text(),
            authors = value.optionalTextList("authors"),
            contentType = ContentType.valueOf(value.field("contentType").text()),
            languageTags = value.optionalTextList("languageTags").toSet(),
        )

    private fun mapDirectCatalogMapping(value: BoundValueReader) = DirectCatalogMapping(
        catalogPluginId = value.field("catalogPluginId").text(),
        catalogSourceId = value.field("catalogSourceId").text(),
    )

    private fun mapRelease(value: BoundValueReader) = SourceChapterRelease(
        sourceReleaseId = value.field("sourceReleaseId").text(),
        sourceUrl = value.field("sourceUrl").text(),
        languageTag = value.field("languageTag").text(),
        rawTitle = value.field("rawTitle").text(),
        rawVolume = value.optionalField("rawVolume")?.text(),
        rawChapter = value.optionalField("rawChapter")?.text(),
        rawPart = value.optionalField("rawPart")?.text(),
        kindHint = value.optionalField("kindHint")?.text()
            ?.let(ChapterKindHint::valueOf) ?: ChapterKindHint.UNKNOWN,
        normalizedVolumeHint = value.optionalField("normalizedVolumeHint")?.text(),
        normalizedChapterHint = value.optionalField("normalizedChapterHint")?.text(),
        normalizedPartHint = value.optionalField("normalizedPartHint")?.text(),
        normalizedTitleHint = value.optionalField("normalizedTitleHint")?.text(),
        translatorOrUploader = value.optionalField("translatorOrUploader")?.text(),
        publishedAtEpochMillis = value.optionalField("publishedAtEpochMillis")?.long(),
        updatedAtEpochMillis = value.optionalField("updatedAtEpochMillis")?.long(),
        contentFingerprint = value.optionalField("contentFingerprint")?.text(),
    )

    private fun optionalToken(value: SelectorBoundValue?, path: String): String? =
        if (value == null || value == SelectorBoundValue.Null) null
        else BoundValueReader(value, path).text()
}
