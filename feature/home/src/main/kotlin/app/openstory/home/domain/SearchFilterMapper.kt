package app.openstory.home.domain

import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogMultiSelectFilter
import app.openstory.plugin.api.catalog.CatalogRangeFilter
import app.openstory.plugin.api.catalog.CatalogSelectFilter
import app.openstory.plugin.api.catalog.CatalogSortFilter
import app.openstory.plugin.api.catalog.CatalogTextFilter

internal fun CatalogFilterDefinition.toSearchFilterDefinition(): SearchFilterDefinition = when (this) {
    is CatalogSelectFilter -> toOptionFilter(SearchOptionFilterKind.SELECT)
    is CatalogMultiSelectFilter -> toOptionFilter(SearchOptionFilterKind.MULTI_SELECT)
    is CatalogSortFilter -> toOptionFilter(SearchOptionFilterKind.SORT)
    is CatalogRangeFilter -> SearchRangeFilterDefinition(
        id = id,
        label = label,
        minimum = minimum,
        maximum = maximum,
        step = step,
    )
    is CatalogTextFilter -> SearchTextFilterDefinition(
        id = id,
        label = label,
        placeholder = placeholder,
    )
}

private fun CatalogFilterDefinition.toOptionFilter(
    kind: SearchOptionFilterKind,
): SearchOptionFilterDefinition {
    val options = when (this) {
        is CatalogSelectFilter -> options
        is CatalogMultiSelectFilter -> options
        is CatalogSortFilter -> options
        else -> error("Filter does not expose options")
    }
    return SearchOptionFilterDefinition(
        id = id,
        label = label,
        kind = kind,
        options = options.map { option -> SearchFilterOption(option.value, option.label) },
    )
}
