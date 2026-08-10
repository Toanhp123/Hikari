package app.openstory.catalog.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openstory.catalog.search.CatalogSearchFilterGroup
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceOptionFilter
import app.openstory.catalog.source.SourceRangeFilter
import app.openstory.catalog.source.SourceTextFilter
import app.openstory.common.id.PluginId
import java.math.BigDecimal
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun SearchFilters(
    groups: List<CatalogSearchFilterGroup>,
    selectedValues: Map<PluginId, Map<String, List<String>>>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
    onClear: (PluginId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleGroups = groups.filter { it.definitions.isNotEmpty() }
    if (visibleGroups.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleGroups.forEach { group ->
            FilterGroup(
                group = group,
                selectedValues = selectedValues[group.pluginId].orEmpty(),
                onValuesChange = onValuesChange,
                onClear = onClear,
            )
        }
    }
}

@Composable
private fun FilterGroup(
    group: CatalogSearchFilterGroup,
    selectedValues: Map<String, List<String>>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
    onClear: (PluginId) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(group.pluginId.value)
            if (selectedValues.isNotEmpty()) {
                OutlinedButton(onClick = { onClear(group.pluginId) }) { Text("Clear") }
            }
        }
        group.definitions.forEach { definition ->
            FilterControl(
                pluginId = group.pluginId,
                definition = definition,
                selected = selectedValues[definition.id].orEmpty(),
                onValuesChange = onValuesChange,
            )
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
    when (definition) {
        is SourceOptionFilter -> OptionFilter(pluginId, definition, selected, onValuesChange)
        is SourceRangeFilter -> RangeFilter(pluginId, definition, selected, onValuesChange)
        is SourceTextFilter -> TextFilter(pluginId, definition, selected, onValuesChange)
    }
}

@Composable
private fun OptionFilter(
    pluginId: PluginId,
    definition: SourceOptionFilter,
    selected: List<String>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(definition.label)
        LazyRow(
            contentPadding = PaddingValues(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(definition.options, key = { it.value }) { option ->
                FilterChip(
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
    val minimum = definition.min
    val maximum = definition.max
    val step = definition.step
    if (minimum == null || maximum == null || step == null || step <= 0.0 || maximum <= minimum) {
        TextFilter(
            pluginId,
            SourceTextFilter(definition.id, definition.label),
            selected,
            onValuesChange,
        )
        return
    }
    val current = selected.firstOrNull()?.toDoubleOrNull()?.coerceIn(minimum, maximum) ?: minimum
    val steps = ((maximum - minimum) / step).roundToInt().minus(1).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${definition.label}: ${formatRangeValue(current)}")
        Slider(
            value = current.toFloat(),
            onValueChange = { raw ->
                val snapped = (minimum + round((raw - minimum) / step) * step).coerceIn(minimum, maximum)
                onValuesChange(pluginId, definition.id, listOf(formatRangeValue(snapped)))
            },
            modifier = Modifier.semantics { contentDescription = "${definition.label} range" },
            valueRange = minimum.toFloat()..maximum.toFloat(),
            steps = steps,
        )
    }
}

private fun nextOptionValues(multiple: Boolean, selected: List<String>, value: String): List<String> =
    if (multiple) {
        if (value in selected) selected - value else selected + value
    } else {
        if (selected.singleOrNull() == value) emptyList() else listOf(value)
    }

private fun formatRangeValue(value: Double): String = BigDecimal.valueOf(value)
    .stripTrailingZeros()
    .toPlainString()
