@file:Suppress("TooManyFunctions")

package app.openstory.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openstory.home.domain.SearchCatalogFilters
import app.openstory.home.domain.SearchFilterDefinition
import app.openstory.home.domain.SearchOptionFilterDefinition
import app.openstory.home.domain.SearchOptionFilterKind
import app.openstory.home.domain.SearchRangeFilterDefinition
import app.openstory.home.domain.SearchResultCard
import app.openstory.home.domain.SearchResultSource
import app.openstory.home.domain.SearchTextFilterDefinition
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import java.math.BigDecimal
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun SearchScreen(
    state: SearchScreenState,
    onQueryChange: (String) -> Unit,
    onFilterValuesChange: (PluginId, String, List<String>) -> Unit,
    onStoryClick: (SearchStorySelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            label = { Text("Search catalogs") },
            singleLine = true,
        )
        RecentSearches(
            queries = state.recentQueries,
            onQueryClick = onQueryChange,
        )
        SearchFilters(
            filters = state.page.filters,
            selectedValues = state.filterValues,
            onValuesChange = onFilterValuesChange,
        )
        SearchResults(
            state = state,
            onStoryClick = onStoryClick,
        )
    }
}

@Composable
private fun RecentSearches(
    queries: List<String>,
    onQueryClick: (String) -> Unit,
) {
    if (queries.isEmpty()) return

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Recent searches", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            queries.take(MAX_VISIBLE_RECENT).forEach { query ->
                AssistChip(
                    onClick = { onQueryClick(query) },
                    label = { Text(query) },
                )
            }
        }
    }
}

@Composable
private fun SearchFilters(
    filters: List<SearchCatalogFilters>,
    selectedValues: Map<PluginId, Map<String, List<String>>>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
) {
    if (filters.all { it.definitions.isEmpty() }) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { catalog ->
            Text(
                text = catalog.pluginId.value,
                style = MaterialTheme.typography.titleSmall,
            )
            catalog.definitions.forEach { definition ->
                FilterDefinitionRow(
                    pluginId = catalog.pluginId,
                    definition = definition,
                    selected = selectedValues[catalog.pluginId]?.get(definition.id).orEmpty(),
                    onValuesChange = onValuesChange,
                )
            }
        }
    }
}

@Composable
private fun FilterDefinitionRow(
    pluginId: PluginId,
    definition: SearchFilterDefinition,
    selected: List<String>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
) {
    when (definition) {
        is SearchOptionFilterDefinition -> OptionFilterRow(
            pluginId = pluginId,
            definition = definition,
            selected = selected,
            onValuesChange = onValuesChange,
        )
        is SearchTextFilterDefinition -> OutlinedTextField(
            value = selected.firstOrNull().orEmpty(),
            onValueChange = { value ->
                onValuesChange(pluginId, definition.id, listOfNotNull(value.takeIf(String::isNotBlank)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(definition.label) },
            placeholder = definition.placeholder?.let { placeholder -> { Text(placeholder) } },
            singleLine = true,
        )
        is SearchRangeFilterDefinition -> RangeFilterControl(
            pluginId = pluginId,
            definition = definition,
            selected = selected,
            onValuesChange = onValuesChange,
        )
    }
}

@Composable
private fun OptionFilterRow(
    pluginId: PluginId,
    definition: SearchOptionFilterDefinition,
    selected: List<String>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(definition.label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            definition.options.forEach { option ->
                val isSelected = option.value in selected
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onValuesChange(
                            pluginId,
                            definition.id,
                            nextOptionValues(definition.kind, selected, option.value),
                        )
                    },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun RangeFilterControl(
    pluginId: PluginId,
    definition: SearchRangeFilterDefinition,
    selected: List<String>,
    onValuesChange: (PluginId, String, List<String>) -> Unit,
) {
    val current = selected.firstOrNull()?.toDoubleOrNull()
        ?.coerceIn(definition.minimum, definition.maximum)
        ?: definition.minimum
    val discreteSteps = ((definition.maximum - definition.minimum) / definition.step)
        .roundToInt()
        .minus(1)
        .coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(definition.label, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = current.toFloat(),
            onValueChange = { value ->
                val snapped = snapRangeValue(value.toDouble(), definition)
                onValuesChange(pluginId, definition.id, listOf(formatRangeValue(snapped)))
            },
            modifier = Modifier.semantics {
                contentDescription = "${definition.label} range"
            },
            valueRange = definition.minimum.toFloat()..definition.maximum.toFloat(),
            steps = discreteSteps,
        )
    }
}

private fun snapRangeValue(
    value: Double,
    definition: SearchRangeFilterDefinition,
): Double {
    val stepsFromMinimum = round((value - definition.minimum) / definition.step)
    return (definition.minimum + stepsFromMinimum * definition.step)
        .coerceIn(definition.minimum, definition.maximum)
}

private fun formatRangeValue(value: Double): String = BigDecimal.valueOf(value)
    .stripTrailingZeros()
    .toPlainString()

@Composable
private fun SearchResults(
    state: SearchScreenState,
    onStoryClick: (SearchStorySelection) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (state.page.searching) {
            item(key = "searching") {
                Text("Searching catalogs…", modifier = Modifier.padding(16.dp))
            }
        }
        state.page.error?.let { error ->
            item(key = "search-error") {
                Text("Search cache unavailable: ${error.code}", modifier = Modifier.padding(16.dp))
            }
        }
        state.page.failures.forEach { (pluginId, error) ->
            item(key = "failure:${pluginId.value}") {
                Text("${pluginId.value}: ${error.code}", modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        itemsIndexed(
            items = state.page.results,
            key = { _, card -> card.storyId.value },
        ) { _, card ->
            SearchResultRow(
                card = card,
                onClick = {
                    card.sources.firstOrNull()?.let { source ->
                        onStoryClick(
                            SearchStorySelection(
                                storyId = card.storyId,
                                pluginId = source.pluginId,
                                sourceId = source.sourceId,
                            ),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    card: SearchResultCard,
    onClick: () -> Unit,
) {
    val semantics = buildSearchSemantics(card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = semantics }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(card.title, style = MaterialTheme.typography.titleMedium)
        Text(card.contentType.name, style = MaterialTheme.typography.bodySmall)
        card.sources.forEach { source ->
            Text(sourceLabel(source), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun nextOptionValues(
    kind: SearchOptionFilterKind,
    selected: List<String>,
    value: String,
): List<String> = when (kind) {
    SearchOptionFilterKind.SELECT,
    SearchOptionFilterKind.SORT,
    -> if (selected.singleOrNull() == value) emptyList() else listOf(value)
    SearchOptionFilterKind.MULTI_SELECT -> if (value in selected) selected - value else selected + value
}

private fun buildSearchSemantics(card: SearchResultCard): String = buildString {
    append(card.title)
    append(", ")
    append(card.contentType.name)
    card.sources.forEach { source ->
        append(", ")
        append(sourceLabel(source))
    }
}

private fun sourceLabel(source: SearchResultSource): String = buildString {
    append(source.pluginId.value)
    source.score?.let { score ->
        append(" score ")
        append(score)
        source.scoreScale?.let { scale ->
            append(" of ")
            append(scale)
        }
    }
}

data class SearchStorySelection(
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceId: String,
)

private const val MAX_VISIBLE_RECENT = 4
