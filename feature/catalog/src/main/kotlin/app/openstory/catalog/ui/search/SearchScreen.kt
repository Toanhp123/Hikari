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
import androidx.compose.ui.unit.dp
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onRecentSelected: (String) -> Unit,
    onFilterValuesChange: (PluginId, String, List<String>) -> Unit,
    onClearFilters: (PluginId) -> Unit,
    onStorySelected: (StoryId) -> Unit,
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

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Recent searches",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(queries.take(MAX_VISIBLE_RECENT), key = { it }) { query ->
                AssistChip(onClick = { onSelected(query) }, label = { Text(query) })
            }
        }
    }
}

@Composable
private fun SearchResults(state: SearchUiState, onStorySelected: (StoryId) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.searching) {
            item(key = "search-progress") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        state.failures.forEach { failure ->
            item(key = "search-failure-${failure.pluginId.value}") {
                Text(
                    text = "${failure.pluginId.value}: ${failure.code}",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        items(state.stories, key = { it.story.id.value }) { result ->
            SearchResultCard(
                result = result,
                onClick = { onStorySelected(result.story.id) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

private const val MAX_VISIBLE_RECENT = 4
