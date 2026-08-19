package app.openstory.catalog.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import app.openstory.catalog.ui.components.catalogDisplayName
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.common.id.PluginId
import app.openstory.designsystem.content.HikariSectionTitle
import app.openstory.designsystem.control.HikariSuggestionChip
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariFocusedHeader
import app.openstory.designsystem.layout.HikariSearchBar
import app.openstory.designsystem.layout.HikariStickyDestinationScaffold
import app.openstory.designsystem.layout.withScreenContentInsets
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.theme.hikariSpacing
import kotlinx.coroutines.launch

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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val headerScrolled = remember {
        derivedStateOf { listState.canScrollBackward }
    }
    val showScrollToTop = remember {
        derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_TO_TOP_ITEM_THRESHOLD }
    }
    HikariDestinationScaffold(modifier) {
        HikariStickyDestinationScaffold(
            contentPadding = contentPadding,
            header = {
                SearchStickyHeader(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    focusManager = focusManager,
                    onBack = onBack,
                )
            },
            headerScrolled = headerScrolled.value,
            showScrollToTop = showScrollToTop.value,
            onScrollToTop = { coroutineScope.launch { listState.animateScrollToItem(0) } },
        ) { bodyPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("search-content"),
                contentPadding = bodyPadding.withScreenContentInsets(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
            ) {
                item(key = "search-intro") { SearchGuidance() }
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
}

@Composable
private fun SearchStickyHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    focusManager: FocusManager,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
        HikariFocusedHeader("Search", onBack)
        HikariSearchBar(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Title, author, or alias",
            contentDescription = "Search stories",
            modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.screenGutter),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )
    }
}

@Composable
private fun SearchGuidance() {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.sectionContentGap)) {
        HikariSectionTitle("Find your next story")
        Text(
            "Results stay grouped across sources, even when one catalog is unavailable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentSearches(queries: List<String>, onSelected: (String) -> Unit) {
    if (queries.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.sectionContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        items(queries.take(MAX_VISIBLE_RECENT), key = { it }) { query ->
            HikariSuggestionChip(
                onClick = { onSelected(query) },
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
                supportingText = catalogFailureMessage(failure.code, "Try again in a moment."),
            )
        }
    }
    state.failures.forEach { failure ->
        item(key = "search-failure-${failure.pluginId.value}") {
            HikariInlineFeedback(
                message = "${failure.pluginId.catalogDisplayName()} unavailable",
                supportingText = catalogFailureMessage(failure.code, "Results from this source may be missing."),
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
        SearchResultCard(result, { onStorySelected(result) })
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
private const val SCROLL_TO_TOP_ITEM_THRESHOLD = 3
