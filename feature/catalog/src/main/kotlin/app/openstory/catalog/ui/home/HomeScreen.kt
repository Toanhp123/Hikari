package app.openstory.catalog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.ranking.RankedCatalogStory
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onCatalogSelected: (PluginId) -> Unit,
    onCombinedSelected: () -> Unit,
    modifier: Modifier = Modifier,
    coverRenderer: HomeCoverRenderer = PlaceholderHomeCoverRenderer,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = MaterialTheme.hikariSpacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.large),
    ) {
        item(key = "home-header") {
            HomeHeader(state.refreshing, onRefresh, onSearch)
        }
        item(key = "home-sources") {
            CatalogSwitcher(
                catalogs = state.catalogs.map { it.pluginId },
                selectedCatalogId = state.selectedCatalogId,
                onCatalogSelected = onCatalogSelected,
                onCombinedSelected = onCombinedSelected,
            )
        }
        if (state.refreshing) {
            item(key = "home-refreshing") {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Refreshing catalog Home" },
                )
            }
        }
        state.globalFailure?.let { failure ->
            item(key = "home-global-failure") {
                Text(
                    text = failure.code,
                    modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        state.refreshReport?.failed?.keys?.sortedBy { it.value }?.forEach { pluginId ->
            item(key = "home-failure-${pluginId.value}") {
                Text(
                    text = "${pluginId.value} refresh failed; cached content is still available.",
                    modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        state.selectedCatalog?.let { catalog ->
            catalogContent(catalog, onStorySelected, coverRenderer)
        } ?: combinedContent(state.rankedStories, onStorySelected, coverRenderer)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.combinedContent(
    rankedStories: List<RankedCatalogStory>,
    onStorySelected: (StoryId) -> Unit,
    coverRenderer: HomeCoverRenderer,
) {
    item(key = "combined-title") {
        Text(
            text = "Across catalogs",
            modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
    item(key = "combined-row") {
        val entries = rankedStories.mapNotNull { ranked -> ranked.primaryEntry() }
        HomeRow(entries, "Across catalogs", onStorySelected, coverRenderer)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.catalogContent(
    catalog: CatalogHomeSnapshot,
    onStorySelected: (StoryId) -> Unit,
    coverRenderer: HomeCoverRenderer,
) {
    catalog.sections.forEach { section ->
        item(key = "catalog-section-title-${catalog.pluginId.value}-${section.sourceId}") {
            Text(
                text = section.title,
                modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item(key = "catalog-section-items-${catalog.pluginId.value}-${section.sourceId}") {
            HomeRow(section.items, section.title, onStorySelected, coverRenderer)
        }
    }
}

@Composable
private fun HomeRow(
    entries: List<CatalogEntry>,
    sectionTitle: String,
    onStorySelected: (StoryId) -> Unit,
    coverRenderer: HomeCoverRenderer,
) {
    if (entries.isEmpty()) {
        Text(
            text = "No cached catalog stories yet.",
            modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.large),
        )
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.hikariSpacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.medium),
    ) {
        items(
            items = entries,
            key = { entry -> "${entry.pluginId.value}:${entry.sourceId}" },
        ) { entry ->
            HomeCard(entry, sectionTitle, onStorySelected, coverRenderer = coverRenderer)
        }
    }
}

@Composable
private fun HomeHeader(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.hikariSpacing.large),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Home", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small)) {
            Button(onClick = onSearch) { Text("Search") }
            Button(onClick = onRefresh, enabled = !refreshing) { Text("Refresh") }
        }
    }
}

@Composable
private fun CatalogSwitcher(
    catalogs: List<PluginId>,
    selectedCatalogId: PluginId?,
    onCatalogSelected: (PluginId) -> Unit,
    onCombinedSelected: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.hikariSpacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small),
    ) {
        item(key = "combined") {
            FilterChip(
                selected = selectedCatalogId == null,
                onClick = onCombinedSelected,
                label = { Text("Across catalogs") },
            )
        }
        items(catalogs, key = { it.value }) { pluginId ->
            FilterChip(
                selected = selectedCatalogId == pluginId,
                onClick = { onCatalogSelected(pluginId) },
                label = { Text(pluginId.value) },
            )
        }
    }
}

private fun RankedCatalogStory.primaryEntry(): CatalogEntry? = contributions
    .firstOrNull { it.entry.score != null }
    ?.entry
    ?: contributions.firstOrNull()?.entry
