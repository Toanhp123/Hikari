package app.openstory.catalog.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openstory.catalog.ui.components.StoryShelf
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onCatalogSelected: (PluginId) -> Unit,
    onCombinedSelected: () -> Unit,
    onCategorySelected: (DiscoverQuickCategory) -> Unit = { onCatalogSelected(it.pluginId) },
    searchFocusRequester: FocusRequester? = null,
    searchNextFocusRequester: FocusRequester? = null,
    categoryFocusRequester: FocusRequester? = null,
    categoryNextFocusRequester: FocusRequester? = null,
    catalogFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        LazyColumn(
            modifier = modifier.background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = MaterialTheme.hikariSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.large),
        ) {
        item("discover-search") {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .optionalFocusRequester(searchFocusRequester)
                    .optionalNextFocus(searchNextFocusRequester)
                    .clickable(role = Role.Button, onClick = onSearch)
                    .semantics {
                        contentDescription = "Search all stories"
                        traversalIndex = 0f
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) { Text("Search all stories", style = MaterialTheme.typography.titleMedium) }
        }
        state.featured?.let { featured ->
            item("discover-featured") { DiscoverHero(featured, onStorySelected) }
        }
        if (state.quickCategories.isNotEmpty()) {
            item("discover-categories") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.quickCategories, key = DiscoverQuickCategory::key) { category ->
                        val isFirstCategory = category == state.quickCategories.first()
                        FilterChip(
                            selected = state.selectedCatalogId == category.pluginId &&
                                state.selectedSourceId == category.sourceId,
                            onClick = { onCategorySelected(category) },
                            label = { Text(category.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                                .then(
                                    if (isFirstCategory) {
                                        Modifier.optionalFocusRequester(categoryFocusRequester)
                                            .optionalNextFocus(categoryNextFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                ).semantics {
                                contentDescription = "Category ${category.label} from ${category.pluginId.value}"
                                traversalIndex = 2f
                            },
                        )
                    }
                }
            }
        }
        item("discover-sources") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("combined") {
                    FilterChip(
                        state.selectedCatalogId == null,
                        onCombinedSelected,
                        { Text("Across catalogs") },
                        Modifier
                            .then(
                                if (state.quickCategories.isEmpty()) {
                                    Modifier.optionalFocusRequester(categoryFocusRequester)
                                } else {
                                    Modifier
                                },
                            )
                            .optionalFocusRequester(catalogFocusRequester)
                            .semantics { traversalIndex = 3f },
                    )
                }
                items(state.catalogs, key = { it.pluginId.value }) { catalog ->
                    FilterChip(
                        selected = state.selectedCatalogId == catalog.pluginId,
                        onClick = { onCatalogSelected(catalog.pluginId) },
                        label = { Text(catalog.pluginId.value) },
                        modifier = Modifier.semantics { traversalIndex = 3f },
                    )
                }
            }
        }
        if (state.refreshing) {
            item("discover-refreshing") {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().semantics { contentDescription = "Refreshing Discover" },
                )
            }
        }
        state.globalFailure?.let { failure ->
            item("discover-global-failure") { Text(failure.code, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
        state.refreshReport?.failed?.keys?.sortedBy { it.value }?.forEach { pluginId ->
            item("discover-failure-${pluginId.value}") {
                Text("${pluginId.value} refresh failed; cached content is still available.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
        state.shelves.forEach { shelf ->
            item("discover-shelf-${shelf.key}") {
                StoryShelf(
                    title = shelf.title,
                    entries = shelf.entries,
                    onSelected = onStorySelected,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        item("discover-refresh-action") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    "Refresh",
                    modifier = Modifier.heightIn(min = 48.dp).clickable(enabled = !state.refreshing, onClick = onRefresh).padding(14.dp),
                )
            }
        }
        }
    }
}

private fun Modifier.optionalFocusRequester(requester: FocusRequester?): Modifier =
    requester?.let(::focusRequester) ?: this

private fun Modifier.optionalNextFocus(requester: FocusRequester?): Modifier =
    requester?.let { nextRequester ->
        focusProperties {
            next = nextRequester
            down = nextRequester
        }
    } ?: this
