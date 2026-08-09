package app.openstory.home.domain

import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceOptionFilter
import app.openstory.catalog.source.SourceRangeFilter
import app.openstory.catalog.source.SourceTextFilter

internal fun SourceFilter.toSearchFilterDefinition(): SearchFilterDefinition? = when (this) {
    is SourceOptionFilter -> SearchOptionFilterDefinition(
        id = id,
        label = label,
        kind = if (multiple) SearchOptionFilterKind.MULTI_SELECT else SearchOptionFilterKind.SELECT,
        options = options.map { option -> SearchFilterOption(option.value, option.label) },
    )
    is SourceRangeFilter -> {
        val minimum = min
        val maximum = max
        val rangeStep = step
        if (minimum != null && maximum != null && rangeStep != null) SearchRangeFilterDefinition(
            id = id,
            label = label,
            minimum = minimum,
            maximum = maximum,
            step = rangeStep,
        ) else null
    }
    is SourceTextFilter -> SearchTextFilterDefinition(
        id = id,
        label = label,
        placeholder = null,
    )
}
