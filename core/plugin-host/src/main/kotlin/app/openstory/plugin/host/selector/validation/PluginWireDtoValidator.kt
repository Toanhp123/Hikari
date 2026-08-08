package app.openstory.plugin.host.selector.validation

import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy
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

class PluginWireDtoValidator(
    urlPolicy: PluginUrlPolicy,
    limits: PluginOutputLimits = PluginOutputLimits(),
) {
    private val support = OutputValidationSupport(urlPolicy, limits)
    private val catalog = CatalogWireDtoValidator(support, limits)
    private val content = ContentWireDtoValidator(support, limits)

    fun validateCatalogHome(value: List<CatalogSection>): AppResult<List<CatalogSection>> =
        catalog.validateHome(value)

    fun validateCatalogSearch(value: Page<CatalogCard>): AppResult<Page<CatalogCard>> =
        catalog.validateSearch(value)

    fun validateCatalogDetails(value: CatalogDetails): AppResult<CatalogDetails> =
        catalog.validateDetails(value)

    fun validateCatalogFilters(
        value: List<CatalogFilterDefinition>,
    ): AppResult<List<CatalogFilterDefinition>> = catalog.validateFilters(value)

    fun validateContentSearch(
        value: Page<ContentStoryCandidate>,
    ): AppResult<Page<ContentStoryCandidate>> = content.validateSearch(value)

    fun validateContentStory(value: ContentStoryDetails): AppResult<ContentStoryDetails> =
        content.validateStory(value)

    fun validateReleases(
        value: List<SourceChapterRelease>,
    ): AppResult<List<SourceChapterRelease>> = content.validateReleases(value)

    fun validateChapterSyncDelta(value: ChapterSyncDelta): AppResult<ChapterSyncDelta> =
        content.validateSync(value)

    fun validateChapterDocument(value: ChapterDocument): AppResult<ChapterDocument> =
        content.validateChapter(value)
}
