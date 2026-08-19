package app.openstory.catalog.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.catalog.search.CatalogSearchFilterGroup
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceOptionFilter
import app.openstory.catalog.source.SourceRangeFilter
import app.openstory.catalog.source.SourceTextFilter
import app.openstory.common.id.PluginId
import java.math.BigDecimal
import kotlin.math.round
import kotlin.math.roundToInt
import app.openstory.designsystem.control.HikariInlineAction
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariDimensions

internal fun LazyListScope.searchFilterItems(
    groups: List<CatalogSearchFilterGroup>,
    selectedValues: Map<PluginId, Map<String, List<String>>>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
    onClear: (PluginId) -> Unit,
) {
    val visibleGroups = groups.filter { it.definitions.isNotEmpty() }
    visibleGroups.forEach { group ->
        val groupValues = selectedValues[group.pluginId].orEmpty()
        item(key = "search-filter-header-${group.pluginId.value}") {
            FilterGroupHeader(group.pluginId, groupValues, onClear)
        }
        group.definitions.forEach { definition ->
            item(key = "search-filter-${group.pluginId.value}-${definition.id}") {
                FilterControl(
                    pluginId = group.pluginId,
                    definition = definition,
                    selected = groupValues[definition.id].orEmpty(),
                    onValuesChange = onValuesChange,
                )
            }
        }
    }
}

@Composable
private fun FilterGroupHeader(
    pluginId: PluginId,
    selectedValues: Map<String, List<String>>,
    onClear: (PluginId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(pluginId.value, style = MaterialTheme.typography.titleSmall)
        if (selectedValues.isNotEmpty()) {
            HikariInlineAction(
                onClick = { onClear(pluginId) },
                modifier = Modifier.heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
            ) { Text("Clear") }
        }
    }
}

@Composable
private fun FilterControl(
    pluginId: PluginId,
    definition: SourceFilter,
    selected: List<String>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
) {
    Column {
        when (definition) {
            is SourceOptionFilter -> OptionFilter(pluginId, definition, selected, onValuesChange)
            is SourceRangeFilter -> RangeFilter(pluginId, definition, selected, onValuesChange)
            is SourceTextFilter -> TextFilter(pluginId, definition, selected, onValuesChange)
        }
    }
}

@Composable
private fun OptionFilter(
    pluginId: PluginId,
    definition: SourceOptionFilter,
    selected: List<String>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4)) {
        Text(definition.label)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.sectionContentGap),
        ) {
            items(definition.options, key = { it.value }) { option ->
                HikariFilterChip(
                    selected = option.value in selected,
                    onClick = {
                        onValuesChange(
                            pluginId,
                            definition.id,
                            nextOptionValues(definition.multiple, selected, option.value),
                        )
                    },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun TextFilter(
    pluginId: PluginId,
    definition: SourceTextFilter,
    selected: List<String>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
) {
    OutlinedTextField(
        value = selected.firstOrNull().orEmpty(),
        onValueChange = { value ->
            onValuesChange(pluginId, definition.id, value.takeIf(String::isNotBlank)?.let(::listOf).orEmpty())
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(definition.label) },
        singleLine = true,
    )
}

@Composable
private fun RangeFilter(
    pluginId: PluginId,
    definition: SourceRangeFilter,
    selected: List<String>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
) {
    val bounds = definition.validBounds()
    if (bounds == null) {
        TextFilter(
            pluginId,
            SourceTextFilter(definition.id, definition.label),
            selected,
            onValuesChange,
        )
        return
    }
    val (minimum, maximum, step) = bounds
    val committed = selected.firstOrNull()?.toDoubleOrNull()?.coerceIn(minimum, maximum) ?: minimum
    var transient by remember(pluginId, definition.id, committed) {
        mutableFloatStateOf(committed.toFloat())
    }
    val current = snapRangeValue(transient.toDouble(), minimum, maximum, step)
    val steps = ((maximum - minimum) / step).roundToInt().minus(1).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4)) {
        Text("${definition.label}: ${formatRangeValue(current)}")
        Slider(
            value = current.toFloat(),
            onValueChange = { raw ->
                transient = snapRangeValue(raw.toDouble(), minimum, maximum, step).toFloat()
            },
            onValueChangeFinished = {
                onValuesChange(pluginId, definition.id, listOf(formatRangeValue(current)))
            },
            modifier = Modifier.semantics { contentDescription = "${definition.label} range" },
            valueRange = minimum.toFloat()..maximum.toFloat(),
            steps = steps,
        )
    }
}

private fun SourceRangeFilter.validBounds(): RangeBounds? {
    val values = listOfNotNull(min, max, step)
    if (values.size != RANGE_BOUND_VALUE_COUNT) return null

    val (minimum, maximum, increment) = values
    return RangeBounds(minimum, maximum, increment).takeIf(RangeBounds::isValid)
}

private data class RangeBounds(
    val minimum: Double,
    val maximum: Double,
    val step: Double,
) {
    fun isValid(): Boolean = step > 0.0 && maximum > minimum
}

private const val RANGE_BOUND_VALUE_COUNT = 3

private fun nextOptionValues(multiple: Boolean, selected: List<String>, value: String): List<String> =
    if (multiple) {
        if (value in selected) selected - value else selected + value
    } else {
        if (selected.singleOrNull() == value) emptyList() else listOf(value)
    }

internal fun snapRangeValue(
    raw: Double,
    minimum: Double,
    maximum: Double,
    step: Double,
): Double = (minimum + round((raw - minimum) / step) * step).coerceIn(minimum, maximum)

private fun formatRangeValue(value: Double): String = BigDecimal.valueOf(value)
    .stripTrailingZeros()
    .toPlainString()
