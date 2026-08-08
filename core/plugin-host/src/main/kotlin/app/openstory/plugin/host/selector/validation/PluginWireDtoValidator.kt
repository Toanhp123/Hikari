package app.openstory.plugin.host.selector.validation

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogSection

class PluginWireDtoValidator(
    private val urlPolicy: PluginUrlPolicy,
    private val limits: PluginOutputLimits = PluginOutputLimits(),
) {
    fun validateCatalogHome(
        value: List<CatalogSection>,
    ): AppResult<List<CatalogSection>> = validate(value) {
        requireLimit(value.size <= limits.maxOutputSections, "sections")
        requireUnique(value.map(CatalogSection::sourceId), "sections", "sourceId")
        var totalItems = 0
        value.forEachIndexed { sectionIndex, section ->
            requireLimit(
                section.items.size <= limits.maxOutputItemsPerSection,
                "sections.$sectionIndex.items",
            )
            totalItems += section.items.size
            validateCards(section.items, "sections.$sectionIndex.items")
        }
        requireLimit(totalItems <= limits.maxTotalOutputItems, "sections")
    }

    fun validateCatalogSearch(
        value: Page<CatalogCard>,
    ): AppResult<Page<CatalogCard>> = validate(value) {
        requireLimit(value.items.size <= limits.maxOutputItems, "items")
        validateCards(value.items, "items")
    }

    fun validateCatalogDetails(
        value: CatalogDetails,
    ): AppResult<CatalogDetails> = validate(value) {
        value.sourceUrl?.let { validateUrl(it, "details.sourceUrl") }
        value.image?.let { image ->
            validateUrl(image.url, "details.image.url", image.declaredHost)
        }
    }

    fun validateCatalogFilters(
        value: List<CatalogFilterDefinition>,
    ): AppResult<List<CatalogFilterDefinition>> = validate(value) {
        requireLimit(value.size <= limits.maxOutputItems, "filters")
        requireUnique(value.map(CatalogFilterDefinition::id), "filters", "id")
    }

    private fun validateCards(
        cards: List<CatalogCard>,
        path: String,
    ) {
        requireUnique(cards.map(CatalogCard::sourceId), path, "sourceId")
        cards.forEachIndexed { index, card ->
            card.image?.let { image ->
                validateUrl(image.url, "$path.$index.image.url", image.declaredHost)
            }
        }
    }

    private fun validateUrl(
        value: String,
        path: String,
        declaredHost: String? = null,
    ) {
        when (val decision = urlPolicy.resolve(value)) {
            is AppResult.Failure -> {
                val code = if (decision.error.code == "plugin.domain_denied") {
                    "plugin.output_undeclared_host"
                } else {
                    "plugin.output_invalid_url"
                }
                throw OutputValidationFailure(code, path)
            }
            is AppResult.Success -> if (
                declaredHost != null && decision.value.host != declaredHost
            ) {
                throw OutputValidationFailure("plugin.output_undeclared_host", path)
            }
        }
    }

    private fun requireUnique(
        values: List<String>,
        path: String,
        field: String,
    ) {
        val seen = mutableSetOf<String>()
        values.forEachIndexed { index, value ->
            if (!seen.add(value)) {
                throw OutputValidationFailure(
                    code = "plugin.output_duplicate_id",
                    path = "$path.$index.$field",
                )
            }
        }
    }

    private fun requireLimit(
        withinLimit: Boolean,
        path: String,
    ) {
        if (!withinLimit) {
            throw OutputValidationFailure("plugin.output_limit", path)
        }
    }
}

private class OutputValidationFailure(
    val code: String,
    val path: String,
) : RuntimeException(null, null, false, false)

private inline fun <T> validate(
    value: T,
    block: () -> Unit,
): AppResult<T> = try {
    block()
    AppResult.Success(value)
} catch (failure: OutputValidationFailure) {
    AppResult.Failure(
        AppError.Plugin(
            code = failure.code,
            retryable = false,
            diagnostic = AppError.Diagnostic.of("field_path" to failure.path),
        ),
    )
}
