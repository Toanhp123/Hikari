package app.openstory.plugin.host.selector.validation

import app.openstory.common.AppResult
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogSection

internal class CatalogWireDtoValidator(
    private val support: OutputValidationSupport,
    private val limits: PluginOutputLimits,
) {
    fun validateHome(value: List<CatalogSection>): AppResult<List<CatalogSection>> =
        validateOutput(value) {
            support.requireLimit(value.size <= limits.maxOutputSections, "sections")
            support.requireUnique(value.map(CatalogSection::sourceId), "sections", "sourceId")
            var totalItems = 0
            value.forEachIndexed { sectionIndex, section ->
                support.requireLimit(
                    section.items.size <= limits.maxOutputItemsPerSection,
                    "sections.$sectionIndex.items",
                )
                totalItems += section.items.size
                validateCards(section.items, "sections.$sectionIndex.items")
            }
            support.requireLimit(totalItems <= limits.maxTotalOutputItems, "sections")
        }

    fun validateSearch(value: Page<CatalogCard>): AppResult<Page<CatalogCard>> =
        validateOutput(value) {
            support.requireLimit(value.items.size <= limits.maxOutputItems, "items")
            validateCards(value.items, "items")
        }

    fun validateDetails(value: CatalogDetails): AppResult<CatalogDetails> =
        validateOutput(value) {
            value.sourceUrl?.let { support.validateUrl(it, "details.sourceUrl") }
            value.image?.let { image ->
                support.validateUrl(image.url, "details.image.url", image.declaredHost)
            }
        }

    fun validateFilters(
        value: List<CatalogFilterDefinition>,
    ): AppResult<List<CatalogFilterDefinition>> = validateOutput(value) {
        support.requireLimit(value.size <= limits.maxOutputItems, "filters")
        support.requireUnique(value.map(CatalogFilterDefinition::id), "filters", "id")
    }

    private fun validateCards(
        cards: List<CatalogCard>,
        path: String,
    ) {
        support.requireUnique(cards.map(CatalogCard::sourceId), path, "sourceId")
        cards.forEachIndexed { index, card ->
            card.image?.let { image ->
                support.validateUrl(image.url, "$path.$index.image.url", image.declaredHost)
            }
        }
    }
}
