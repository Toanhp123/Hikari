package app.openstory.plugin.api.selector.catalog

import app.openstory.plugin.api.selector.ListBinding
import app.openstory.plugin.api.selector.ObjectBinding
import app.openstory.plugin.api.selector.OptionalBinding
import app.openstory.plugin.api.selector.SelectorBinding
import app.openstory.plugin.api.selector.SelectorOutputShape
import app.openstory.plugin.api.selector.SelectorTextValueBinding
import app.openstory.plugin.api.selector.SelectorTokenKind
import app.openstory.plugin.api.selector.UrlBinding
import app.openstory.plugin.api.selector.SelectorValidation
import app.openstory.plugin.api.selector.SelectorValidationErrorCode
import app.openstory.plugin.api.selector.selectorFail
import app.openstory.plugin.api.selector.validateOutputObject

object CatalogSelectorValidation {
    fun validateHome(selector: CatalogHomeSelector): Result<Unit> = runCatching {
        SelectorValidation.validateBinding(selector.sections).getOrThrow()

        val sections =
            requireListBinding(
                selector.sections,
                "catalog.home.sections",
            )

        validateOutputObject(
            binding =
                requireObjectBinding(
                    sections.item,
                    "catalog.home.sections[]",
                ),
            shape = SECTION_SHAPE,
            path = "catalog.home.sections[]",
        )
    }

    private fun requireListBinding(
        binding: SelectorBinding,
        path: String,
    ): ListBinding =
        binding as? ListBinding
            ?: selectorFail(
                SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
                "Output binding type mismatch at $path.",
            )

    private fun requireObjectBinding(
        binding: SelectorBinding,
        path: String,
    ): ObjectBinding =
        binding as? ObjectBinding
            ?: selectorFail(
                SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
                "Output binding type mismatch at $path.",
            )

    fun validateSearch(selector: CatalogSearchSelector): Result<Unit> = runCatching {
        SelectorValidation.validateBinding(selector.items).getOrThrow()
        selector.nextToken?.let {
            SelectorValidation.validateBinding(it).getOrThrow()
            validateTokenBinding(
                binding = it,
                kind = selector.nextTokenKind,
                path = "catalog.search.nextToken",
            )
        }
        val items =
            requireListBinding(
                selector.items,
                "catalog.search.items",
            )

        validateOutputObject(
            binding =
                requireObjectBinding(
                    items.item,
                    "catalog.search.items[]",
                ),
            shape = CARD_SHAPE,
            path = "catalog.search.items[]",
        )
    }

    fun validateDetails(selector: CatalogDetailsSelector): Result<Unit> = runCatching {
        SelectorValidation.validateBinding(selector.details).getOrThrow()
        validateOutputObject(
            binding =
                requireObjectBinding(
                    selector.details,
                    "catalog.details",
                ),
            shape = DETAILS_SHAPE,
            path = "catalog.details",
        )
    }

    fun validateFilters(selector: CatalogFiltersSelector): Result<Unit> = runCatching {
        val ids = selector.filters.map(CatalogFilterBinding::id)
        if (ids.distinct().size != ids.size) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_CONSTANT,
                "Catalog filter IDs must be unique.",
            )
        }
        if (selector.filters.size > MAX_FILTERS) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_CONSTANT,
                "Catalog filter count exceeds the host limit.",
            )
        }
        selector.filters.forEach(::validateFilter)
    }

    private fun validateFilter(filter: CatalogFilterBinding) {
        if (filter.id.isBlank() || filter.label.isBlank()) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_CONSTANT,
                "Catalog filter ID and label must not be blank.",
            )
        }

        when (filter) {
            is CatalogSelectFilterBinding -> validateOptions(filter.options)
            is CatalogMultiSelectFilterBinding -> validateOptions(filter.options)
            is CatalogSortFilterBinding -> validateOptions(filter.options)
            is CatalogRangeFilterBinding -> validateRange(filter)
            is CatalogTextFilterBinding -> {
                if (filter.placeholder?.any(Char::isISOControl) == true) {
                    selectorFail(
                        SelectorValidationErrorCode.INVALID_CONSTANT,
                        "Catalog text filter placeholder is invalid.",
                    )
                }
            }
        }
    }

    private fun validateOptions(options: List<CatalogFilterOptionBinding>) {
        if (options.isEmpty() || options.size > MAX_OPTIONS) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_CONSTANT,
                "Catalog filter option count is invalid.",
            )
        }
        if (options.any { it.value.isBlank() || it.label.isBlank() }) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_CONSTANT,
                "Catalog filter options must not be blank.",
            )
        }
        if (options.map(CatalogFilterOptionBinding::value).distinct().size != options.size) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_CONSTANT,
                "Catalog filter option values must be unique.",
            )
        }
    }

    private fun validateRange(filter: CatalogRangeFilterBinding) {
        val boundsAreFinite =
            filter.minimum.isFinite() && filter.maximum.isFinite()
        val stepIsValid =
            filter.step.isFinite() && filter.step > 0.0
        val rangeIsOrdered =
            filter.minimum < filter.maximum

        if (!boundsAreFinite || !stepIsValid || !rangeIsOrdered) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_CONSTANT,
                "Catalog range filter configuration is invalid.",
            )
        }
    }

    private fun validateTokenBinding(
        binding: SelectorBinding,
        kind: SelectorTokenKind,
        path: String,
    ) {
        val unwrapped = if (binding is OptionalBinding) binding.value else binding
        val matches = when (kind) {
            SelectorTokenKind.OPAQUE -> unwrapped is SelectorTextValueBinding
            SelectorTokenKind.URL -> unwrapped is UrlBinding
        }
        if (!matches) {
            selectorFail(
                SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
                "Output binding type mismatch at $path.",
            )
        }
    }

    private const val MAX_FILTERS = 64
    private const val MAX_OPTIONS = 200

    private val IMAGE_SHAPE = SelectorOutputShape.Object(
        required = mapOf("url" to SelectorOutputShape.Url),
        optional = mapOf("declaredHost" to SelectorOutputShape.Text),
    )

    private val SCORE_SHAPE = SelectorOutputShape.Object(
        required = mapOf(
            "value" to SelectorOutputShape.Double,
            "scale" to SelectorOutputShape.Double,
        ),
    )

    private val CARD_SHAPE = SelectorOutputShape.Object(
        required = mapOf(
            "sourceId" to SelectorOutputShape.Text,
            "title" to SelectorOutputShape.Text,
        ),
        optional = mapOf(
            "authors" to SelectorOutputShape.TextList,
            "image" to IMAGE_SHAPE,
            "score" to SCORE_SHAPE,
        ),
    )

    private val SECTION_SHAPE = SelectorOutputShape.Object(
        required = mapOf(
            "sourceId" to SelectorOutputShape.Text,
            "title" to SelectorOutputShape.Text,
            "items" to SelectorOutputShape.List(CARD_SHAPE),
        ),
    )

    private val DETAILS_SHAPE = SelectorOutputShape.Object(
        required = mapOf(
            "sourceId" to SelectorOutputShape.Text,
            "title" to SelectorOutputShape.Text,
            "contentType" to SelectorOutputShape.Enum,
            "languageTags" to SelectorOutputShape.TextSet,
        ),
        optional = mapOf(
            "sourceUrl" to SelectorOutputShape.Url,
            "aliases" to SelectorOutputShape.TextList,
            "authors" to SelectorOutputShape.TextList,
            "description" to SelectorOutputShape.Text,
            "genres" to SelectorOutputShape.TextList,
            "image" to IMAGE_SHAPE,
            "score" to SCORE_SHAPE,
            "popularityRank" to SelectorOutputShape.Long,
        ),
    )
}
