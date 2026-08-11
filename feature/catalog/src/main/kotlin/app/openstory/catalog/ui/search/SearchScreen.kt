package app.openstory.catalog.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.common.id.PluginId
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onRecentSelected: (String) -> Unit,
    onFilterValuesChange: (PluginId, String, List<String>) -> Unit,
    onClearFilters: (PluginId) -> Unit,
    onStorySelected: (CatalogSearchStory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.hikariSpacing.large),
            label = { Text("Search catalogs") },
            singleLine = true,
        )
        RecentSearches(state.recentQueries, onRecentSelected)
        SearchFilters(
            groups = state.filterGroups,
            selectedValues = state.filterValues,
            onValuesChange = onFilterValuesChange,
            onClear = onClearFilters,
        )
        SearchResults(state, onStorySelected)
    }
}

@Composable
private fun RecentSearches(queries: List<String>, onSelected: (String) -> Unit) {
    if (queries.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.extraSmall)) {
        Text(
            text = "Recent searches",
            modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = MaterialTheme.hikariSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small),
        ) {
            items(queries.take(MAX_VISIBLE_RECENT), key = { it }) { query ->
                AssistChip(onClick = { onSelected(query) }, label = { Text(query) })
            }
        }
    }
}

@Composable
private fun SearchResults(state: SearchUiState, onStorySelected: (CatalogSearchStory) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MaterialTheme.hikariSpacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small),
    ) {
        if (state.searching) {
            item(key = "search-progress") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        state.globalFailure?.let { failure ->
            item(key = "search-global-failure") {
                Text(
                    text = failure.code,
                    modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        state.failures.forEach { failure ->
            item(key = "search-failure-${failure.pluginId.value}") {
                Text(
                    text = "${failure.pluginId.value}: ${failure.code}",
                    modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        items(state.stories, key = { it.story.id.value }) { result ->
            SearchResultCard(
                result = result,
                onClick = { onStorySelected(result) },
                modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
            )
        }
    }
}

private const val MAX_VISIBLE_RECENT = 4
