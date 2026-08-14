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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.common.id.PluginId
import app.openstory.designsystem.content.HikariSectionTitle
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariFocusedHeader
import app.openstory.designsystem.layout.HikariSearchBar
import app.openstory.designsystem.layout.plus
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariDimensions

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
            contentPadding = contentPadding.plus(bottom = MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
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
        Modifier.padding(
            horizontal = MaterialTheme.hikariSpacing.space16,
            vertical = MaterialTheme.hikariSpacing.space8,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space10),
    ) {
        HikariSectionTitle("Find your next story")
        Text(
            "Results stay grouped across sources, even when one catalog is unavailable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HikariSearchBar(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Title, author, or alias",
            contentDescription = "Search stories",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )
    }
}

@Composable
private fun RecentSearches(queries: List<String>, onSelected: (String) -> Unit) {
    if (queries.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.hikariSpacing.space16),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        item {
            Text(
                text = "Recent",
                modifier = Modifier.padding(top = MaterialTheme.hikariSpacing.space12),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        items(queries.take(MAX_VISIBLE_RECENT), key = { it }) { query ->
            AssistChip(
                onClick = { onSelected(query) },
                modifier = Modifier.heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
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
            HikariInlineFeedback(
                message = "Search unavailable",
                supportingText = failure.code,
                modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space16),
            )
        }
    }
    state.failures.forEach { failure ->
        item(key = "search-failure-${failure.pluginId.value}") {
            HikariInlineFeedback(
                message = "${failure.pluginId.value} unavailable",
                supportingText = failure.code,
                modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space16),
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
            Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space16),
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

private const val MAX_VISIBLE_RECENT = 4
