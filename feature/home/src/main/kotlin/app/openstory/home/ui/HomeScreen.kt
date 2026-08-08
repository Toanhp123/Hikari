package app.openstory.home.ui

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
import androidx.compose.ui.unit.dp
import app.openstory.home.model.HomeCombinedCard
import app.openstory.model.PluginId

@Composable
fun HomeScreen(
    state: HomeScreenState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    coverRenderer: HomeCoverRenderer = PlaceholderHomeCoverRenderer,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "home-header") {
            HomeHeader(
                refreshing = state.refreshing,
                onRefresh = actions.refresh,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "home-sources") {
            CatalogSwitcher(
                catalogs = state.home.catalogs.map { it.pluginId },
                selectedCatalogId = null,
                onCatalogSelected = actions.catalogSelected,
            )
        }
        if (state.refreshing) {
            item(key = "home-refreshing") {
                HomeRefreshIndicator()
            }
        }
        state.refreshReport?.failed
            ?.keys
            ?.sortedBy { it.value }
            ?.forEach { pluginId ->
                item(key = "home-failure-${pluginId.value}") {
                    RefreshFailureMessage(
                        pluginId = pluginId,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        item(key = "combined-title") {
            Text(
                text = "Across catalogs",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item(key = "combined-row") {
            CombinedHomeContent(
                combined = state.home.combined,
                actions = actions,
                coverRenderer = coverRenderer,
            )
        }
    }
}

@Composable
private fun CombinedHomeContent(
    combined: List<HomeCombinedCard>,
    actions: HomeActions,
    coverRenderer: HomeCoverRenderer,
) {
    if (combined.isEmpty()) {
        Text(
            text = "No cached catalog stories yet.",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = combined,
                key = { it.storyId.value },
            ) { item ->
                CombinedHomeCard(
                    combined = item,
                    actions = actions,
                    coverRenderer = coverRenderer,
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineMedium,
        )
        Button(
            onClick = onRefresh,
            enabled = !refreshing,
        ) {
            Text("Refresh")
        }
    }
}

@Composable
internal fun CatalogSwitcher(
    catalogs: List<PluginId>,
    selectedCatalogId: PluginId?,
    onCatalogSelected: (PluginId) -> Unit,
    modifier: Modifier = Modifier,
    onCombinedSelected: (() -> Unit)? = null,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onCombinedSelected != null) {
            item(key = "combined") {
                FilterChip(
                    selected = selectedCatalogId == null,
                    onClick = onCombinedSelected,
                    label = { Text("Across catalogs") },
                )
            }
        }
        items(
            items = catalogs,
            key = { it.value },
        ) { pluginId ->
            FilterChip(
                selected = pluginId == selectedCatalogId,
                onClick = { onCatalogSelected(pluginId) },
                label = { Text(pluginId.value) },
            )
        }
    }
}

@Composable
internal fun HomeRefreshIndicator() {
    LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Refreshing catalog Home"
            },
    )
}

@Composable
internal fun RefreshFailureMessage(
    pluginId: PluginId,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "${pluginId.value} refresh failed; cached content is still available.",
        modifier = modifier,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CombinedHomeCard(
    combined: HomeCombinedCard,
    actions: HomeActions,
    coverRenderer: HomeCoverRenderer,
) {
    val primary = combined.sources.firstOrNull { source ->
        source.score != null && source.scoreScale != null
    } ?: combined.sources.firstOrNull() ?: return
    val section = primary.sections.firstOrNull()?.title ?: "Across catalogs"
    HomeCard(
        card = HomeCardPresentation(
            storyId = combined.storyId,
            title = primary.title,
            contentType = primary.contentType,
            authors = primary.authors,
            coverReference = primary.coverReference,
            score = primary.score,
            scoreScale = primary.scoreScale,
            scoreSource = primary.pluginId,
            sectionTitle = section,
        ),
        onClick = actions.storySelected,
        coverRenderer = coverRenderer,
    )
}
