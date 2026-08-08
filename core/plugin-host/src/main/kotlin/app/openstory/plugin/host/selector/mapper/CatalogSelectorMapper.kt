package app.openstory.plugin.host.selector.mapper

import app.openstory.common.AppResult
import app.openstory.model.ContentType
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogFilterOption
import app.openstory.plugin.api.catalog.CatalogImageReference
import app.openstory.plugin.api.catalog.CatalogMultiSelectFilter
import app.openstory.plugin.api.catalog.CatalogRangeFilter
import app.openstory.plugin.api.catalog.CatalogScore
import app.openstory.plugin.api.catalog.CatalogSection
import app.openstory.plugin.api.catalog.CatalogSelectFilter
import app.openstory.plugin.api.catalog.CatalogSortFilter
import app.openstory.plugin.api.catalog.CatalogTextFilter
import app.openstory.plugin.api.selector.SelectorTokenKind
import app.openstory.plugin.api.selector.catalog.CatalogFilterBinding
import app.openstory.plugin.api.selector.catalog.CatalogFilterOptionBinding
import app.openstory.plugin.api.selector.catalog.CatalogMultiSelectFilterBinding
import app.openstory.plugin.api.selector.catalog.CatalogRangeFilterBinding
import app.openstory.plugin.api.selector.catalog.CatalogSelectFilterBinding
import app.openstory.plugin.api.selector.catalog.CatalogSortFilterBinding
import app.openstory.plugin.api.selector.catalog.CatalogTextFilterBinding
import app.openstory.plugin.host.selector.binding.SelectorBoundValue
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator

class CatalogSelectorMapper(
    private val outputValidator: PluginWireDtoValidator,
    private val urlPolicy: PluginUrlPolicy,
) {
    fun mapHome(
        value: SelectorBoundValue,
    ): AppResult<List<CatalogSection>> =
        mapBoundOutput("sections") {
            BoundValueReader(value, "sections").values().map(::mapSection)
        }.flatMap(outputValidator::validateCatalogHome)

    fun mapSearch(
        items: SelectorBoundValue,
        nextToken: SelectorBoundValue?,
        nextTokenKind: SelectorTokenKind,
    ): AppResult<Page<CatalogCard>> =
        mapBoundOutput("items") {
            Page(
                items = BoundValueReader(items, "items").values().map(::mapCard),
                nextToken = mapToken(nextToken, nextTokenKind),
            )
        }.flatMap(outputValidator::validateCatalogSearch)

    fun mapDetails(
        value: SelectorBoundValue,
    ): AppResult<CatalogDetails> =
        mapBoundOutput("details") {
            val details = BoundValueReader(value, "details")
            CatalogDetails(
                sourceId = details.field("sourceId").text(),
                sourceUrl = details.optionalField("sourceUrl")?.text(),
                title = details.field("title").text(),
                aliases = details.optionalTextList("aliases"),
                authors = details.optionalTextList("authors"),
                description = details.optionalField("description")?.text(),
                genres = details.optionalTextList("genres"),
                contentType = ContentType.valueOf(details.field("contentType").text()),
                languageTags = details.optionalTextList("languageTags").toSet(),
                image = details.optionalField("image")?.let(::mapImage),
                score = details.optionalField("score")?.let(::mapScore),
                popularityRank = details.optionalField("popularityRank")?.long(),
            )
        }.flatMap(outputValidator::validateCatalogDetails)

    fun mapFilters(
        values: List<CatalogFilterBinding>,
    ): AppResult<List<CatalogFilterDefinition>> =
        mapBoundOutput("filters") { values.map(::mapFilter) }
            .flatMap(outputValidator::validateCatalogFilters)

    private fun mapSection(value: BoundValueReader): CatalogSection = CatalogSection(
        sourceId = value.field("sourceId").text(),
        title = value.field("title").text(),
        items = value.field("items").values().map(::mapCard),
    )

    private fun mapCard(value: BoundValueReader): CatalogCard = CatalogCard(
        sourceId = value.field("sourceId").text(),
        title = value.field("title").text(),
        authors = value.optionalTextList("authors"),
        image = value.optionalField("image")?.let(::mapImage),
        score = value.optionalField("score")?.let(::mapScore),
    )

    private fun mapImage(value: BoundValueReader): CatalogImageReference {
        val url = value.field("url").text()
        val decision = urlPolicy.resolve(url)
        val validated = (decision as? AppResult.Success)?.value
            ?: throw SelectorMappingFailure("plugin.selector_field_invalid", "${value.path}.url")
        return CatalogImageReference(
            url = validated.value,
            declaredHost = value.optionalField("declaredHost")?.text() ?: validated.host,
        )
    }

    private fun mapScore(value: BoundValueReader): CatalogScore = CatalogScore(
        value = value.field("value").double(),
        scale = value.field("scale").double(),
    )

    private fun mapToken(
        value: SelectorBoundValue?,
        kind: SelectorTokenKind,
    ): String? {
        if (value == null || value == SelectorBoundValue.Null) return null
        val token = BoundValueReader(value, "nextToken").text()
        return if (kind == SelectorTokenKind.URL) {
            val decision = urlPolicy.resolve(token)
            (decision as? AppResult.Success)?.value?.value
                ?: throw SelectorMappingFailure("plugin.selector_field_invalid", "nextToken")
        } else {
            token
        }
    }

    private fun mapFilter(value: CatalogFilterBinding): CatalogFilterDefinition = when (value) {
        is CatalogSelectFilterBinding -> CatalogSelectFilter(
            value.id,
            value.label,
            value.options.map(::mapOption),
        )
        is CatalogMultiSelectFilterBinding -> CatalogMultiSelectFilter(
            value.id,
            value.label,
            value.options.map(::mapOption),
        )
        is CatalogRangeFilterBinding -> CatalogRangeFilter(
            value.id,
            value.label,
            value.minimum,
            value.maximum,
            value.step,
        )
        is CatalogTextFilterBinding -> CatalogTextFilter(
            value.id,
            value.label,
            value.placeholder,
        )
        is CatalogSortFilterBinding -> CatalogSortFilter(
            value.id,
            value.label,
            value.options.map(::mapOption),
        )
    }

    private fun mapOption(value: CatalogFilterOptionBinding) =
        CatalogFilterOption(value.value, value.label)
}
