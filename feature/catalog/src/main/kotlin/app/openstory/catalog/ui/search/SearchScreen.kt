package app.openstory.catalog.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.common.id.PluginId
import app.openstory.designsystem.glass.HikariGlassSurface
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariFocusedHeader
import app.openstory.designsystem.layout.plus
import app.openstory.designsystem.state.HikariEmptyState
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
    onBack: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val focusManager = LocalFocusManager.current
    HikariDestinationScaffold(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("search-content"),
            contentPadding = contentPadding.plus(bottom = MaterialTheme.hikariSpacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small),
        ) {
            item(key = "search-navigation") { HikariFocusedHeader("Search", onBack) }
            item(key = "search-header") { SearchHeader(state.query, onQueryChange, focusManager) }
            if (state.recentQueries.isNotEmpty()) {
                item(key = "search-recents") { RecentSearches(state.recentQueries, onRecentSelected) }
            }
            if (state.filterGroups.any { it.definitions.isNotEmpty() }) {
                searchFilterItems(state.filterGroups, state.filterValues, onFilterValuesChange, onClearFilters)
            }
            searchResultItems(state, onStorySelected)
        }
    }
}

@Composable
private fun SearchHeader(query: String, onQueryChange: (String) -> Unit, focusManager: FocusManager) {
    Column(
        Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large, vertical = MaterialTheme.hikariSpacing.small),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Find your next story", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Results stay grouped across sources, even when one catalog is unavailable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HikariGlassSurface(
            backdropScope = null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Title, author, or alias") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        }
    }
}

@Composable
private fun RecentSearches(queries: List<String>, onSelected: (String) -> Unit) {
    if (queries.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.hikariSpacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small),
    ) {
        item { Text("Recent", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge) }
        items(queries.take(MAX_VISIBLE_RECENT), key = { it }) { query ->
            AssistChip(
                onClick = { onSelected(query) },
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(query) },
            )
        }
    }
}

private fun LazyListScope.searchResultItems(
    state: SearchUiState,
    onStorySelected: (CatalogSearchStory) -> Unit,
) {
    if (state.searching) item(key = "search-progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    state.globalFailure?.let { failure ->
        item(key = "search-global-failure") {
            FailureBanner(
                "Search unavailable",
                failure.code,
                Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
            )
        }
    }
    state.failures.forEach { failure ->
        item(key = "search-failure-${failure.pluginId.value}") {
            FailureBanner(
                "${failure.pluginId.value} unavailable",
                failure.code,
                Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
            )
        }
    }
    if (state.shouldShowEmptyState) {
        item(key = "search-empty") {
            HikariEmptyState(
                title = "No matches found",
                message = "Try another title, author, or alias.",
            )
        }
    }
    items(state.stories, key = { it.story.id.value }) { result ->
        SearchResultCard(
            result,
            { onStorySelected(result) },
            Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
        )
    }
}

private val SearchUiState.shouldShowEmptyState: Boolean
    get() = when {
        query.isBlank() -> false
        searching -> false
        stories.isNotEmpty() -> false
        failures.isNotEmpty() -> false
        globalFailure != null -> false
        else -> true
    }

@Composable
private fun FailureBanner(title: String, code: String, modifier: Modifier = Modifier) {
    HikariGlassSurface(null, modifier.fillMaxWidth(), RoundedCornerShape(18.dp), PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            Text(code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MAX_VISIBLE_RECENT = 4
